package skunk.net

import cats.effect.{ Concurrent, ConcurrentEffect, Resource }
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.concurrent.Signal
import fs2.Stream
import skunk.{ Command, Query, ~, Encoder, Decoder, Void }
import skunk.data._
import skunk.net.message. { Query => QueryMessage, _ }
import skunk.util.Namer
import skunk.syntax.id._

/**
 * Interface for a Postgres database, expressed through high-level operations that rely on exchange
 * of multiple messages. Operations here can be executed concurrently. Note that resource safety
 * is not guaranteed here: statements and portals must be closed explicitly.
 */
trait Protocol[F[_]] {

  /**
   * Unfiltered stream of all asynchronous channel notifications sent to this session. In general
   * this stream is consumed asynchronously and the associated fiber is canceled before the
   * session ends.
   * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def notifications(maxQueued: Int): Stream[F, Notification]

  /**
   * Signal representing the current state of all Postgres configuration variables announced to this
   * session. These are sent after authentication and are updated asynchronously if the runtime
   * environment changes. The current keys are as follows (with example values), but these may
   * change with future releases so you should be prepared to handle unexpected ones.
   *
   * {{{
   * Map(
   *   "application_name"            -> "",
   *   "client_encoding"             -> "UTF8",
   *   "DateStyle"                   -> "ISO, MDY",
   *   "integer_datetimes"           -> "on",       // cannot change after startup
   *   "IntervalStyle"               -> "postgres",
   *   "is_superuser"                -> "on",
   *   "server_encoding"             -> "UTF8",     // cannot change after startup
   *   "server_version"              -> "9.5.3",    // cannot change after startup
   *   "session_authorization"       -> "postgres",
   *   "standard_conforming_strings" -> "on",
   *   "TimeZone"                    -> "US/Pacific",
   * )
   * }}}
   */
  def parameters: Signal[F, Map[String, String]]

  /**
   * Prepare a command (a statement that produces no rows), yielding a `Protocol.PreparedCommand` which
   * must be closed by the caller. Higher-level APIs may wish to encapsulate this in a `Resource`.
   */
  def prepareCommand[A](command: Command[A]): F[Protocol.PreparedCommand[F, A]]

  /**
   * Prepare a query (a statement that produces rows), yielding a `Protocol.PreparedCommand` which
   * must be closed by the caller. Higher-level APIs may wish to encapsulate this in a `Resource`.
   */
  def prepareQuery[A, B](query: Query[A, B]): F[Protocol.PreparedQuery[F, A, B]]

  /**
   * Execute a non-parameterized command (a statement that produces no rows), yielding a
   * `Completion`. This is equivalent to `prepare` + `bind` + `execute` but it uses the "simple"
   * query protocol which requires fewer message exchanges.
   */
  def quick(command: Command[Void]): F[Completion]

  /**
   * Execute a non-parameterized query (a statement that produces rows), yielding all rows. This is
   * equivalent to `prepare` + `bind` + `execute` but it uses the "simple" query protocol which
   * requires fewer message exchanges. If you wish to page or stream results you need to use the
   * general protocol instead.
   */
  def quick[A](query: Query[Void, A]): F[List[A]]

  /**
   * Initiate the session. This must be the first thing you do. This is very basic at the momemnt.
   */
  def startup(user: String, database: String): F[Unit]

  /**
   * Signal representing the current transaction status as reported by `ReadyForQuery`. It's not
   * clear that this is a useful thing to expose.
   */
  def transactionStatus: Signal[F, TransactionStatus]

}

object Protocol {

  trait CommandPortal[F[_]] {
    def close: F[Unit]
    def execute: F[Completion]
  }

  trait PreparedCommand[F[_], A] {
    def bind(args: A): F[Protocol.CommandPortal[F]]
    def check: F[Unit]
    def close: F[Unit]
  }


  trait PreparedQuery[F[_], A, B] {
    def close: F[Unit]
    def check: F[Unit]
    def bind(args: A): F[Protocol.QueryPortal[F, B]]
  }


  trait QueryPortal[F[_], A] {
    def close: F[Unit]
    def execute(maxRows: Int): F[List[A] ~ Boolean]
  }


  /**
   * Resource yielding a new `Protocol` with the given `host`, `port`, and statement checking policy.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param check Check all `prepare` and `quick` statements for consistency with the schema. This
   *   is true by default and is recommended for development work.
   */
  def apply[F[_]: ConcurrentEffect](
    host:  String,
    port:  Int     = 5432,
    check: Boolean = true
  ): Resource[F, Protocol[F]] =
    for {
      ams <- BufferedMessageSocket[F](host, port)
      ses <- Resource.liftF(Protocol.fromBufferedMessageSocket(ams, check))
    } yield ses

  /** Construct a `Protocol` by wrapping an `BufferedMessageSocket`. */
  private def fromBufferedMessageSocket[F[_]: Concurrent](
    ams:   BufferedMessageSocket[F],
    check: Boolean
  ): F[Protocol[F]] =
    for {
      nam <- Namer[F]
      sem <- Semaphore[F](1)
    } yield new SessionImpl(ams, nam, sem, check)

  /**
   * `Protocol` implementation.
   * @param ams `BufferedMessageSocket` that manages message exchange.
   * @param nam `Namer` for giving unique (per session) names for prepared statements and portals.
   * @param sem Single-key `Semaphore` used as a mutex for message exchanges. Every "conversation"
   *   must be conducted while holding the mutex because we may have interleaved streams.
   * @param check Check all `prepare` and `quick` statements for consistency with the schema.
   */
  private final class SessionImpl[F[_]: Concurrent](
    ams:   BufferedMessageSocket[F],
    nam:   Namer[F],
    sem:   Semaphore[F],
    check: Boolean
  ) extends Protocol[F] {

    def notifications(maxQueued: Int): Stream[F, Notification] =
      ams.notifications(maxQueued)

    def parameters: Signal[F, Map[String, String]] =
      ams.parameters

    def prepareCommand[A](command: Command[A]): F[Protocol.PreparedCommand[F, A]] =
      parse(command.sql, command.encoder).map { stmt =>
        new Protocol.PreparedCommand[F, A] {

          def bind(args: A): F[Protocol.CommandPortal[F]] =
            bindStmt(stmt, command.encoder, args).map { portal =>
              new Protocol.CommandPortal[F] {

                def close: F[Unit] =
                  closePortal(portal)

                def execute: F[Completion] =
                  sem.withPermit {
                    for {
                      _  <- ams.send(Execute(portal, 0))
                      _  <- ams.send(Flush)
                      c  <- ams.expect { case CommandComplete(c) => c }
                    } yield c
                  }

              }
            }

          def check: F[Unit] =
            sem.withPermit {
              for {
                _  <- ams.send(Describe.statement(stmt))
                _  <- ams.send(Flush)
                pd <- ams.expect { case pd @ ParameterDescription(_) => pd }
                _  <- ams.expect { case NoData => }
                _  <- printStatement(command.sql)
                _  <- checkParameterDescription(pd, command.encoder)
              } yield ()
            }

          def close: F[Unit] =
            closeStmt(stmt)

        }
      } //.flatMap { ps => ps.check.whenA(check).as(ps) }

    def prepareQuery[A, B](query: Query[A, B]): F[Protocol.PreparedQuery[F, A, B]] =
      parse(query.sql, query.encoder).map { stmt =>
        new Protocol.PreparedQuery[F, A, B] {

          def bind(args: A): F[Protocol.QueryPortal[F, B]] =
            bindStmt(stmt, query.encoder, args).map { portal =>
              new Protocol.QueryPortal[F, B] {

                def close: F[Unit] =
                  closePortal(portal)

                def execute(maxRows: Int): F[List[B] ~ Boolean] =
                  sem.withPermit {
                    for {
                      _  <- ams.send(Execute(portal, maxRows))
                      _  <- ams.send(Flush)
                      rs <- unroll(query.decoder)
                    } yield rs
                  }

              }
            }

          def check: F[Unit] =
            sem.withPermit {
              for {
                _  <- ams.send(Describe.statement(stmt))
                _  <- ams.send(Flush)
                pd <- ams.expect { case pd @ ParameterDescription(_) => pd }
                fs <- ams.expect { case rd @ RowDescription(_) => rd }
                _  <- printStatement(query.sql)
                _  <- checkParameterDescription(pd, query.encoder)
                _  <- checkRowDescription(fs, query.decoder)
              } yield ()
            }

          def close: F[Unit] =
            closeStmt(stmt)

        }
      } .flatMap { ps => ps.check.whenA(check).as(ps) }

    def quick(command: Command[Void]): F[Completion] =
      sem.withPermit {
        ams.send(QueryMessage(command.sql)) *> ams.flatExpect {

          case CommandComplete(c) =>
            for {
              // _ <- printStatement(command.sql).whenA(check)
              _ <- ams.expect { case ReadyForQuery(_) => }
            } yield c

           case ErrorResponse(e) =>
            for {
              _ <- ams.expect { case ReadyForQuery(_) => }
              c <- Concurrent[F].raiseError[Completion](new SqlException(e))
            } yield c

        }
      }

    def quick[B](query: Query[Void, B]): F[List[B]] =
      sem.withPermit {
        ams.send(QueryMessage(query.sql)) *> ams.flatExpect {

          case rd @ RowDescription(_) =>
            for {
              // _  <- printStatement(query.sql).whenA(check)
              // _  <- checkRowDescription(rd, query.decoder).whenA(check)
              rs <- unroll(query.decoder).map(_._1) // rs._2 will always be true here
              _  <- ams.expect { case ReadyForQuery(_) => }
            } yield rs

          case ErrorResponse(e) =>
            for {
              _  <- ams.expect { case ReadyForQuery(_) => }
              rs <- Concurrent[F].raiseError[List[B]](new SqlException(e))
            } yield rs
          }

      }


    // Startup negotiation. Very basic right now.
    def startup(user: String, database: String): F[Unit] =
      sem.withPermit {
        for {
          _ <- ams.send(StartupMessage(user, database))
          _ <- ams.expect { case AuthenticationOk => }
          _ <- ams.expect { case ReadyForQuery(_) => }
        } yield ()
      }

    def transactionStatus: Signal[F, TransactionStatus] =
      ams.transactionStatus

    // HELPERS

    private def closePortal(name: String): F[Unit] =
      sem.withPermit {
        for {
          _ <- ams.send(Close.portal(name))
          _ <- ams.send(Flush)
          _ <- ams.expect { case CloseComplete => }
        } yield ()
      }

    private def closeStmt(name: String): F[Unit] =
      sem.withPermit {
        for {
          _ <- ams.send(Close.statement(name))
          _ <- ams.send(Flush)
          _ <- ams.expect { case CloseComplete => }
        } yield ()
      }

    private def recover[A](t: Throwable): F[A] =
      for {
        _ <- ams.send(Sync)
        _ <- ams.expect { case ReadyForQuery(_) => }
        a <- Concurrent[F].raiseError[A](t)
      } yield a

    private def parse[A](sql: String, enc: Encoder[A]): F[String] =
      sem.withPermit {
        for {
          n <- nam.nextName("stmt")
          _ <- ams.send(Parse(n, sql, enc.types.toList))
          _ <- ams.send(Flush)
          _ <- ams.flatExpect {
                 case ParseComplete    => ().pure[F]
                 case ErrorResponse(e) => recover[Unit](new SqlException(e))
               }
        } yield n
      }

    private def bindStmt[A](stmt: String, enc: Encoder[A], args: A): F[String] =
      sem.withPermit {
        for {
          pn <- nam.nextName("portal")
          _  <- ams.send(Bind(pn, stmt, enc.encode(args)))
          _  <- ams.send(Flush)
          _  <- ams.expect { case BindComplete => }
        } yield pn
      }

    private def unroll[A](dec: Decoder[A]): F[List[A] ~ Boolean] = {
      def go(accum: List[A]): F[List[A] ~ Boolean] =
        ams.receive.flatMap {
          case rd @ RowData(_)         => go(dec.decode(rd.fields) :: accum)
          case      CommandComplete(_) => (accum.reverse ~ false).pure[F]
          case      PortalSuspended    => (accum.reverse ~ true).pure[F]
        }
      go(Nil)
    }

    private def printStatement(sql: String): F[Unit] = {
      def print(a: Any) = Concurrent[F].delay(println(a))
      for {
        _  <- print("**")
        _  <- sql.lines.toList.traverse(s => print("** " + s))
        _  <- print("**")
      } yield ()
    }

    private def checkRowDescription(rd: RowDescription, dec: Decoder[_]): F[Unit] = {
      def print(a: Any) = Concurrent[F].delay(println(a))
      val assertedFieldTypes = dec.types
      val fs = rd.oids
      for {
        _  <- print("** Fields:     asserted: " + assertedFieldTypes.map(_.name).mkString(", "))
        _  <- print("**               actual: " + fs.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
        _  <- print("**")
      } yield ()
    }

    private def checkParameterDescription(pd: ParameterDescription, enc: Encoder[_]): F[Unit] = {
      def print(a: Any) = Concurrent[F].delay(println(a))
      val assertedParameterTypes = enc.types
      val ps = pd.oids
      for {
        _  <- print("** Parameters: asserted: " + assertedParameterTypes.map(_.name).mkString(", "))
        _  <- print("**               actual: " + ps.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
        _  <- print("**")
      } yield ()
    }

  }

}


