package skunk
package proto

import cats.effect.{ Concurrent, ConcurrentEffect, Resource }
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.concurrent.Signal
import fs2.Stream
import skunk.data._
import skunk.net.ActiveMessageSocket
import skunk.net.message. { Query => QueryMessage, _ }
import skunk.util.Namer
import skunk.syntax.id._

/**
 * Represents a live connection to a Postgres database. This is a lifetime-managed resource and as
 * such is invalid outside the scope of its owning `Resource`, as are any streams yielded here. If
 * you construct a stream and never run it, no problem. But if you do run it you must do so while
 * the session is valid, and you must consume all input as it arrives. This interface provides
 * access to operations that are defined in terms of message exchange, without much higher-level
 * abstraction.
 */
trait ProtoSession[F[_]] {
  def notifications(maxQueued: Int): Stream[F, Notification]
  def parameters: Signal[F, Map[String, String]]
  def prepareCommand[A](command: Command[A]): F[ProtoPreparedCommand[F, A]]
  def prepareQuery[A, B](query: Query[A, B]): F[ProtoPreparedQuery[F, A, B]]
  def quick(command: Command[Void]): F[Completion]
  def quick[A](query: Query[Void, A]): F[List[A]]
  def startup(user: String, database: String): F[Unit]
  def transactionStatus: Signal[F, TransactionStatus]
}

object ProtoSession {

  /**
   * Resource yielding a new `ProtoSession` with the given `host`, `port`, and statement checking policy.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param check Check all `prepare` and `quick` statements for consistency with the schema. This
   *   is true by default and is recommended for development work. You may wish to turn this off in
   *   production but honestly it's really cheap and probably worth keeping.
   */
  def apply[F[_]: ConcurrentEffect](
    host:  String,
    port:  Int     = 5432,
    check: Boolean = true
  ): Resource[F, ProtoSession[F]] =
    for {
      ams <- ActiveMessageSocket[F](host, port)
      ses <- Resource.liftF(ProtoSession.fromActiveMessageSocket(ams, check))
    } yield ses

  /** Construct a `ProtoSession` by wrapping an `ActiveMessageSocket`. */
  private def fromActiveMessageSocket[F[_]: Concurrent](
    ams:   ActiveMessageSocket[F],
    check: Boolean
  ): F[ProtoSession[F]] =
    for {
      nam <- Namer[F]
      sem <- Semaphore[F](1)
    } yield new SessionImpl(ams, nam, sem, check)

  /**
   * `ProtoSession` implementation.
   * @param ams `ActiveMessageSocket` that manages message exchange.
   * @param nam `Namer` for giving unique (per session) names for prepared statements and portals.
   * @param sem Single-key `Semaphore` used as a mutex for message exchanges. Every "conversation"
   *   must be conducted while holding the mutex because we may have interleaved streams.
   * @param check Check all `prepare` and `quick` statements for consistency with the schema.
   */
  private final class SessionImpl[F[_]: Concurrent](
    ams:   ActiveMessageSocket[F],
    nam:   Namer[F],
    sem:   Semaphore[F],
    check: Boolean
    // what if here we had statement pools? there's probably no reason not to cache prepared
    // statements "forever". we map strings to names, which lets us use the same statement even
    // if it has different associated encoders and decoders … it's all the same on the PG side.
    //  statementCache: MVar[Map[Sql, Name]]
    //
    // we also need to track the portals we open because they need to be cleaned up when the
    // session is returned to the pool.
    //  portalCache: MVar[Set[String]]
    // and def closePortals: close all the cursors
  ) extends ProtoSession[F] {

    def notifications(maxQueued: Int): Stream[F, Notification] =
      ams.notifications(maxQueued)

    def parameters: Signal[F, Map[String, String]] =
      ams.parameters

    def prepareCommand[A](command: Command[A]): F[ProtoPreparedCommand[F, A]] =
      parse(command.sql, command.encoder).map { stmt =>
        new ProtoPreparedCommand[F, A] {

          def bind(args: A): F[ProtoCommandPortal[F]] =
            bindStmt(stmt, command.encoder, args).map { portal =>
              new ProtoCommandPortal[F] {

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
      } .flatMap { ps => ps.check.whenA(check).as(ps) }

    def prepareQuery[A, B](query: Query[A, B]): F[ProtoPreparedQuery[F, A, B]] =
      parse(query.sql, query.encoder).map { stmt =>
        new ProtoPreparedQuery[F, A, B] {

          def bind(args: A): F[ProtoQueryPortal[F, B]] =
            bindStmt(stmt, query.encoder, args).map { portal =>
              new ProtoQueryPortal[F, B] {

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
        for {
          _ <- ams.send(QueryMessage(command.sql))
          _ <- printStatement(command.sql).whenA(check)
          c <- ams.expect { case CommandComplete(c) => c }
          _ <- ams.expect { case ReadyForQuery(_) => }
        } yield c
      }

    def quick[B](query: Query[Void, B]): F[List[B]] =
      sem.withPermit {
        for {
          _  <- ams.send(QueryMessage(query.sql))
          rd <- ams.expect { case rd @ RowDescription(_) => rd }
          _  <- printStatement(query.sql).whenA(check)
          _  <- checkRowDescription(rd, query.decoder).whenA(check)
          rs <- unroll(query.decoder).map(_._1) // rs._2 will always be true here
          _  <- ams.expect { case ReadyForQuery(_) => }
        } yield rs
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

    private def parse[A](sql: String, enc: Encoder[A]): F[String] =
      sem.withPermit {
        for {
          n <- nam.nextName("stmt")
          _ <- ams.send(Parse(n, sql, enc.oids.toList))
          _ <- ams.send(Flush)
          _ <- ams.expect { case ParseComplete => }
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
      val assertedFieldTypes = dec.oids
      val fs = rd.oids
      for {
        _  <- print("** Fields:     asserted: " + assertedFieldTypes.map(_.name).mkString(", "))
        _  <- print("**               actual: " + fs.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
        _  <- print("**")
      } yield ()
    }

    private def checkParameterDescription(pd: ParameterDescription, enc: Encoder[_]): F[Unit] = {
      def print(a: Any) = Concurrent[F].delay(println(a))
      val assertedParameterTypes = enc.oids
      val ps = pd.oids
      for {
        _  <- print("** Parameters: asserted: " + assertedParameterTypes.map(_.name).mkString(", "))
        _  <- print("**               actual: " + ps.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
        _  <- print("**")
      } yield ()
    }

  }

}


