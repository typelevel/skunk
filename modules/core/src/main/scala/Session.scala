package skunk

import cats.effect.{ Concurrent, ConcurrentEffect, Resource }
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.concurrent.Signal
import fs2.Stream
import scala.annotation.implicitNotFound
import shapeless._
import skunk.net._
import skunk.proto.message. { Query => QueryMessage, _ }
import skunk.util.Namer

trait Session[F[_]] {

  // When we prepare a statement the result is dependent on its session. Really they should
  // depend on the *transaction* but we dont have transactions modeled yet.
  type PreparedQuery[A, B]
  type PreparedCommand[A]

  def startup(user: String, database: String): F[Unit]

  def parameters: Signal[F, Map[String, String]]
  def transactionStatus: Signal[F, ReadyForQuery.Status]

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

  // def check[A, B](query: PreparedQuery[A, B]): F[Unit]
  // def check[A](command: PreparedCommand[A]): F[Unit]

  // We're not going to worry about exposing portals. Execution will create the portal, fetch the
  // data, and close the portal on complete. We will need to hold the mutex for every fetch, which
  // can in principle allow two queries to have their results interleaved. This would be a good
  // testcase.
  protected def executeImpl[A, B](query: PreparedQuery[A, B], args: A): Stream[F, B] = ???
  // protected def executeImpl[A](query: PreparedCommand[A], args: A): F[CommandComplete]

  object execute {
    def apply[A <: HList, B](query: PreparedQuery[A, B]) = new Partial(query)
    class Partial[A, B](query: PreparedQuery[A, B]) extends ProductArgs {
      def applyProduct(a: A): Stream[F, B] = executeImpl(query, a)
    }

    // overload for PreparedCommand
  }

  object prepareAndExecute // etc

}

object Session {

  @implicitNotFound("Only simple (non-parameterized) statements are allowed here.")
  sealed trait NonParameterized[A]
  object NonParameterized {
    implicit val UnitNonParameterized: NonParameterized[Unit] = new NonParameterized[Unit] {}
    implicit val HNilNonParameterized: NonParameterized[HNil] = new NonParameterized[HNil] {}
  }

  def apply[F[_]: ConcurrentEffect](host: String, port: Int): Resource[F, Session[F]] =
    for {
      ams <- ActiveMessageSocket[F](host, port)
      ses <- Resource.liftF(Session.fromActiveMessageSocket(ams))
    } yield ses

  def fromActiveMessageSocket[F[_]: Concurrent](ams: ActiveMessageSocket[F]): F[Session[F]] =
    for {
      nam <- Namer[F]("statement")
      sem <- Semaphore[F](1)
    } yield new SessionImpl(ams, nam, sem)

  private class SessionImpl[F[_]: Concurrent](
    ams: ActiveMessageSocket[F],
    nam: Namer[F],
    sem: Semaphore[F]
  ) extends Session[F] {

    class PreparedQuery[A, B](
      val name:    String,
      val sql:     String,
      val encoder: Encoder[A],
      val decoder: Decoder[B]
    )

    class PreparedCommand[A](
      val name:    String,
      val sql:     String,
      val encoder: Encoder[A]
    )

    // Parameters and Transaction Status are freebies
    def parameters: Signal[F, Map[String, String]] = ams.parameters
    def transactionStatus: Signal[F, ReadyForQuery.Status] = ams.transactionStatus

    // Execute a query and unroll its rows into a List. If no rows are returned it's an error.
    def quick[A: Session.NonParameterized, B](query: Query[A, B]): F[List[B]] =
      sem.withPermit {
        for {
          _  <- ams.send(QueryMessage(query.sql))
          _  <- ams.expect { case rd @ RowDescription(_) => rd } // todo: analyze
          rs <- unroll(query.decoder)
          _  <- ams.expect { case ReadyForQuery(_) => }
        } yield rs
      }

    def quick[A: Session.NonParameterized](command: Command[A]): F[CommandComplete] =
      sem.withPermit {
        for {
          _  <- ams.send(QueryMessage(command.sql))
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
          n <- nam.nextName
          _ <- ams.send(Parse(n, query.sql, query.encoder.oids.toList)) // blergh
          _ <- ams.send(Sync)
          _ <- ams.expect { case ParseComplete => }
          _ <- ams.expect { case ReadyForQuery(_) => }
        } yield new PreparedQuery(n, query.sql, query.encoder, query.decoder)
      } flatMap { ps => analyzeStatement(ps).as(ps) } // TODO: this, but only if there's a flag

    // Parse a command and construct a PreparedCommand for later execution
    def prepare[A](command: Command[A]): F[PreparedCommand[A]] =
      sem.withPermit {
        for {
          n <- nam.nextName
          _ <- ams.send(Parse(n, command.sql, command.encoder.oids.toList)) // blergh
          _ <- ams.send(Sync)
          _ <- ams.expect { case ParseComplete => }
          _ <- ams.expect { case ReadyForQuery(_) => }
        } yield new PreparedCommand(n, command.sql, command.encoder)
      } flatMap { pc => analyzeCommand(pc).as(pc) } // TODO: this, but only if there's a flag

    // Unroll a series of RowData messages into a List. Caller must hold the mutex.
    def unroll[A](dec: Decoder[A], accum: List[A] = Nil): F[List[A]] =
      ams.receive.flatMap {
        case rd @ RowData(_)         => unroll(dec, dec.decode(rd.fields) :: accum)
        case      CommandComplete(_) => accum.reverse.pure[F]
      }

    // Register on the given channel. Caller must hold the mutex. TODO: escape the channel name
    def registerListen(channel: String): F[Unit] =
      for {
        _ <- ams.send(QueryMessage(s"LISTEN $channel"))
        _ <- ams.expect { case CommandComplete("LISTEN") => }
        _ <- ams.expect { case ReadyForQuery(_) => }
      } yield ()

    // Analyze a prepared statement and ensure that the asserted types are correct.
    def analyzeStatement(stmt: PreparedQuery[_, _]): F[Unit] = {
      def print(a: Any) = Concurrent[F].delay(println(a))
      val assertedFieldTypes = stmt.decoder.oids
      val assertedParameterTypes = stmt.encoder.oids
      sem.withPermit {
        for {
          _  <- ams.send(Describe.statement(stmt.name))
          _  <- ams.send(Sync)
          ps <- ams.expect { case ParameterDescription(oids) => oids }
          fs <- ams.expect { case rd @ RowDescription(_) => rd.oids }
          _  <- ams.expect { case ReadyForQuery(_) => }
          _  <- print("**")
          _  <- stmt.sql.lines.toList.traverse(s => print("** " + s))
          _  <- print("**")
          _  <- print("** Parameters: asserted: " + assertedParameterTypes.map(_.name).mkString(", "))
          _  <- print("**               actual: " + ps.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
          _  <- print("**")
          _  <- print("** Fields:     asserted: " + assertedFieldTypes.map(_.name).mkString(", "))
          _  <- print("**               actual: " + fs.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
          _  <- print("**")
        } yield ()
      }
    }

    // Analyze a prepared statement and ensure that the asserted types are correct.
    def analyzeCommand(stmt: PreparedCommand[_]): F[Unit] = {
      def print(a: Any) = Concurrent[F].delay(println(a))
      val assertedParameterTypes = stmt.encoder.oids
      sem.withPermit {
        for {
          _  <- ams.send(Describe.statement(stmt.name))
          _  <- ams.send(Sync)
          ps <- ams.expect { case ParameterDescription(oids) => oids }
          _  <- ams.expect { case NoData => }
          _  <- ams.expect { case ReadyForQuery(_) => }
          _  <- print("**")
          _  <- stmt.sql.lines.toList.traverse(s => print("** " + s))
          _  <- print("**")
          _  <- print("** Parameters: asserted: " + assertedParameterTypes.map(_.name).mkString(", "))
          _  <- print("**               actual: " + ps.map(n => Type.forOid(n).getOrElse(s"«$n»")).mkString(", "))
          _  <- print("**")
        } yield ()
      }
    }

  }

}


