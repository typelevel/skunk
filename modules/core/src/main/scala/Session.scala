package skunk

import cats.effect.{ Concurrent, ConcurrentEffect, Resource }
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.concurrent.Signal
import fs2.{ Chunk, Stream }
import scala.annotation.implicitNotFound
import skunk.net._
import skunk.proto.message. { Query => QueryMessage, _ }
import skunk.util.Namer

/**
 * Represents a live connection to a Postgres database. This is a lifetime-managed resource and as
 * such is invalid outside the scope of its owning `Resource`, as are any streams yielded here. If
 * you construct a stream and never run it, no problem. But if you do run it you must do so while
 * the session is valid, and you must consume all input as it arrives.
 */
trait Session[F[_]] {

  // When we prepare a statement the result is dependent on its session. Really they should
  // depend on the *transaction* but we dont have transactions modeled yet.
  case class PreparedQuery[A, B](name: String, query: Query[A, B])
  case class PreparedCommand[A](name: String, command: Command[A])
  case class Portal[A](name: String, decoder: Decoder[A])

  def startup(user: String, database: String): F[Unit]

  def parameters: Signal[F, Map[String, String]]
  def transactionStatus: Signal[F, ReadyForQuery.Status]

  def parameter(key: String): Stream[F, String] =
    parameters.discrete.map(_.get(key)).unNone.changes

  /**
   * Execute a non-parameterized query and yield all results. This is convenient for queries that
   * will only return a handful of rows. If the extent is unknown it's best to use `prepare` and
   * `excute` which provides constant-memory streaming.
   */
  def quick[A: Session.NonParameterized, B](query: Query[A, B]): F[List[B]]

  /** Execute a non-paramaterized command and yield its result. */
  def quick[A: Session.NonParameterized](command: Command[A]): F[CommandComplete]

  /**
   * A stream that initiates listening on `channel` with the specified maximum queue size. Once
   * this stream starts execution it is important to consume values in a timely fashion, otherwise
   * it will [semantically] block other operations on this session. Typically this stream will be
   * consumed asynchronously via `.to(some sink).compile.drain.start`.
   */
  def listen(channel: String, maxQueued: Int): Stream[F, NotificationResponse]

  /** Send a notification to `channel`. */
  def notify(channel: String, message: String): F[Unit]

  def prepare[A, B](query: Query[A, B]): F[PreparedQuery[A, B]]

  def prepare[A](command: Command[A]): F[PreparedCommand[A]]

  def check[A, B](query: PreparedQuery[A, B]): F[Unit]

  def check[A](command: PreparedCommand[A]): F[Unit]

  def bind[A, B](query: PreparedQuery[A, B], args: A): Resource[F, Portal[B]]

  def execute[A](portal: Portal[A], maxRows: Int): F[List[A] ~ Boolean]

  def stream[A, B](query: PreparedQuery[A, B], args: A, chunkSize: Int): Stream[F, B]


}

object Session {

  @implicitNotFound("Parameterized statements are not allowed here. Use `prepare` instead.")
  sealed trait NonParameterized[A]
  object NonParameterized {
    implicit val UnitNonParameterized: NonParameterized[Void] = new NonParameterized[Void] {}
  }

  /**
   * Resource yielding a new `Session` with the given `host`, `port`, and statement checking policy.
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
  ): Resource[F, Session[F]] =
    for {
      ams <- ActiveMessageSocket[F](host, port)
      ses <- Resource.liftF(Session.fromActiveMessageSocket(ams, check))
    } yield ses

  /** Construct a `Session` by wrapping an `ActiveMessageSocket`. */
  private def fromActiveMessageSocket[F[_]: Concurrent](
    ams:   ActiveMessageSocket[F],
    check: Boolean
  ): F[Session[F]] =
    for {
      nam <- Namer[F]
      sem <- Semaphore[F](1)
    } yield new SessionImpl(ams, nam, sem, check)

  /**
   * `Session` implementation.
   * @param ams `ActiveMessageSocket` that manages message exchange.
   * @param nam `Namer` for giving unique (per session) names for prepared statements and portals.
   * @param sem Single-key `Semaphore` used as a mutex for message exchanges. Every "conversation"
   *   must be conducted while holding the mutex because we may have interleaved streams.
   * @param check Check all `prepare` and `quick` statements for consistency with the schema.
   */
  private class SessionImpl[F[_]: Concurrent](
    ams:   ActiveMessageSocket[F],
    nam:   Namer[F],
    sem:   Semaphore[F],
    check: Boolean
  ) extends Session[F] {

    // Parameters and Transaction Status are freebies
    def parameters: Signal[F, Map[String, String]] = ams.parameters
    def transactionStatus: Signal[F, ReadyForQuery.Status] = ams.transactionStatus

    def bind[A, B](pq: PreparedQuery[A, B], args: A): Resource[F, Portal[B]] =
      Resource.make {
        sem.withPermit {
          for {
            pn <- nam.nextName("portal")
            _  <- ams.send(Bind(pn, pq.name, pq.query.encoder.encode(args)))
            _  <- ams.send(Flush)
            _  <- ams.expect { case BindComplete => }
          } yield Portal(pn, pq.query.decoder)
        }
      } { p =>
        sem.withPermit {
          for {
            _ <- ams.send(Close.portal(p.name))
            _ <- ams.send(Flush)
            _ <- ams.expect { case CloseComplete => }
          } yield ()
        }
      }

    def execute[A](portal: Portal[A], maxRows: Int): F[List[A] ~ Boolean] =
      sem.withPermit {
        for {
          _  <- ams.send(Execute(portal.name, maxRows))
          _  <- ams.send(Flush)
          rs <- unroll(portal.decoder)
        } yield rs
      }

    def stream[A, B](query: PreparedQuery[A, B], args: A, chunkSize: Int): Stream[F, B] =
      Stream.resource(bind(query, args)).flatMap { portal =>
        def chunks: Stream[F, B] =
          Stream.eval(execute(portal, chunkSize)).flatMap {
            case (bs, true)  => Stream.chunk(Chunk.seq(bs)) ++ chunks
            case (bs, false) => Stream.chunk(Chunk.seq(bs))
          }
        chunks
      }

    // Execute a query and unroll its rows into a List. If no rows are returned it's an error.
    def quick[A: Session.NonParameterized, B](query: Query[A, B]): F[List[B]] =
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

    def quick[A: Session.NonParameterized](command: Command[A]): F[CommandComplete] =
      sem.withPermit {
        for {
          _  <- ams.send(QueryMessage(command.sql))
          _  <- printStatement(command.sql).whenA(check)
          cc <- ams.expect { case cc @ CommandComplete(_) => cc }
          _  <- ams.expect { case ReadyForQuery(_) => }
        } yield cc
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

    // A stream that registers for notifications and returns the resulting stream.
    // TODO: bracket listem/unlisten so we stop receiving messages when the stream terminates.
    def listen(channel: String, maxQueued: Int): Stream[F, NotificationResponse] =
      for {
        _ <- Stream.eval(sem.withPermit(registerListen(channel)))
        n <- ams.notifications(maxQueued).filter(_.channel === channel)
      } yield n

    // Send a notification. TODO: the channel and message are not escaped but they should be.
    def notify(channel: String, message: String): F[Unit] =
      sem.withPermit {
        for {
          _ <- ams.send(QueryMessage(s"NOTIFY $channel, '$message'"))
          _ <- ams.expect { case CommandComplete("NOTIFY") => }
          _ <- ams.expect { case ReadyForQuery(_) => }
        } yield ()
      }

    // Parse a statement and construct a PreparedQuery for later execution
    def prepare[A, B](query: Query[A, B]): F[PreparedQuery[A, B]] =
      sem.withPermit {
        for {
          n <- nam.nextName("query")
          _ <- ams.send(Parse(n, query.sql, query.encoder.oids.toList)) // blergh
          _ <- ams.send(Flush)
          _ <- ams.expect { case ParseComplete => }
        } yield PreparedQuery(n, query)
      } flatMap { pq => check(pq).whenA(check).as(pq) }

    // Parse a command and construct a PreparedCommand for later execution
    def prepare[A](command: Command[A]): F[PreparedCommand[A]] =
      sem.withPermit {
        for {
          n <- nam.nextName("command")
          _ <- ams.send(Parse(n, command.sql, command.encoder.oids.toList)) // blergh
          _ <- ams.send(Flush)
          _ <- ams.expect { case ParseComplete => }
        } yield new PreparedCommand(n, command)
      } flatMap { pq => check(pq).whenA(check).as(pq) }

    // Unroll a series of RowData messages into a List. Caller must hold the mutex.
    def unroll[A](dec: Decoder[A]): F[List[A] ~ Boolean] = {
      def go(accum: List[A]): F[List[A] ~ Boolean] =
        ams.receive.flatMap {
          case rd @ RowData(_)         => go(dec.decode(rd.fields) :: accum)
          case      CommandComplete(_) => (accum.reverse ~ false).pure[F]
          case      PortalSuspended    => (accum.reverse ~ true).pure[F]
        }
      go(Nil)
    }

    // Register on the given channel. Caller must hold the mutex. TODO: escape the channel name
    def registerListen(channel: String): F[Unit] =
      for {
        _ <- ams.send(QueryMessage(s"LISTEN $channel"))
        _ <- ams.expect { case CommandComplete("LISTEN") => }
        _ <- ams.expect { case ReadyForQuery(_) => }
      } yield ()

    // Analyze a prepared statement and ensure that the asserted types are correct.
    def check[A, B](stmt: PreparedQuery[A, B]): F[Unit] =
      sem.withPermit {
        for {
          _  <- ams.send(Describe.statement(stmt.name))
          _  <- ams.send(Flush)
          pd <- ams.expect { case pd @ ParameterDescription(_) => pd }
          fs <- ams.expect { case rd @ RowDescription(_) => rd }
          _  <- printStatement(stmt.query.sql)
          _  <- checkParameterDescription(pd, stmt.query.encoder)
          _  <- checkRowDescription(fs, stmt.query.decoder)
        } yield ()
      }

    // Analyze a prepared statement and ensure that the asserted types are correct.
    def check[A](stmt: PreparedCommand[A]): F[Unit] =
      sem.withPermit {
        for {
          _  <- ams.send(Describe.statement(stmt.name))
          _  <- ams.send(Flush)
          pd <- ams.expect { case pd @ ParameterDescription(_) => pd }
          _  <- ams.expect { case NoData => }
          _  <- printStatement(stmt.command.sql)
          _  <- checkParameterDescription(pd, stmt.command.encoder)
        } yield ()
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


