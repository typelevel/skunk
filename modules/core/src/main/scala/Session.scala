package skunk

import cats.effect._
import cats.implicits._
import fs2.{ Chunk, Stream }
import fs2.concurrent.Signal
import skunk.data._
import skunk.net.ProtoSession
import skunk.util.Pool

/**
 * Represents a live connection to a Postgres database. This is a lifetime-managed resource and as
 * such is invalid outside the scope of its owning `Resource`, as are any streams yielded here. If
 * you construct a stream and never run it, no problem. But if you do run it you must do so while
 * the session is valid, and you must consume all input as it arrives.
 */
abstract class Session[F[_]](val protoSession: ProtoSession[F]) {

  /** A prepared query, executable on this `Session` only. */
  type PreparedQuery[A, B] = protoSession.PreparedQuery[A, B]

  /** A prepared command, executable on this `Session` only. */
  type PreparedCommand[A] = protoSession.PreparedCommand[A]

  /** A query portal, readable on this `Session` only. */
  type QueryPortal[A] = protoSession.QueryPortal[A]

  // do we need QueryPortal[A] and CommandPortal?
  // seems like that would make sense

  /** Signal broadcasting changes to the session configuration, which may happen at any time.  */
  def parameters: Signal[F, Map[String, String]] = protoSession.parameters

  /** Signal broadcasting changes for a single configuration key, which may happen at any time.  */
  def parameter(key: String): Stream[F, String] =
    parameters.discrete.map(_.get(key)).unNone.changes

  /** Signal representing the current transaction status. */
  def transactionStatus: Signal[F, TransactionStatus] = protoSession.transactionStatus

  /** Send a notification on the given channel. */
  def notify(channel: Identifier, message: String): F[Unit] = protoSession.notify(channel, message)

  /**
   * Excute a non-parameterized query and yield all results. If you have parameters or wish to limit
   * returned rows use `bind`/`execute` or `stream`.
   */
  def quick[A](query: Query[Void, A]): F[List[A]] = protoSession.quick(query)

  /**
   * Excute a non-parameterized command and yield a `Completion`. If you have parameters use
   * `bind`/`execute`.
   */
  def quick(command: Command[Void]): F[Completion] = protoSession.quick(command)

  /**
   * Fetch the next `maxRows` from `portal`, yielding a list of values and a boolean, `true` if
   * more rows are available, `false` otherwise.
   */
  def execute[A](portal: QueryPortal[A], maxRows: Int): F[List[A] ~ Boolean] = protoSession.execute(portal, maxRows)

  /** Execute the command `portal`, yielding a `Completion`. */
  // def execute(portal: QueryPortal[Void]): F[Completion]

  def check[A, B](query: PreparedQuery[A, B]): F[Unit] = protoSession.check(query)
  def check[A](command: PreparedCommand[A]): F[Unit] = protoSession.check(command)

  def bind[A, B](pq: PreparedQuery[A, B], args: A = Void): Resource[F, QueryPortal[B]]

  def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[A, B]]
  def listen(channel: Identifier, maxQueued: Int): Stream[F, Notification]

  /**
   * Stream that calls `execute` repeatedly and emits chunks until none remain. Note that each
   * chunk is read atomically while holding the session mutex, which means interleaved streams
   * will achieve better fairness with smaller chunks but greater overall throughput with larger
   * chunks. So it's important to consider the use case when specifying `chunkSize`.
   */
  def stream[A, B](query: PreparedQuery[A, B], chunkSize: Int, args: A = Void): Stream[F, B]

  def option[A, B](query: PreparedQuery[A, B], args: A = Void): F[Option[B]]

  def first[A, B](query: PreparedQuery[A, B], args: A = Void): F[B]

}

object Session {

  def pool[F[_]: ConcurrentEffect](
    host:     String,
    port:     Int,
    user:     String,
    database: String,
    max:      Long,
    check:    Boolean = true
  ): SessionPool[F] = {

    val reset: Session[F] => F[Boolean] = s =>
      for {
        // todo: unsubscribe all
        // todo: sync, rollback if necessary
        _ <- s.quick(Command("RESET ALL", Void.codec))
      } yield true

    Pool.of(once(host, port, user, database, check), max, reset)

  }

  def once[F[_]: ConcurrentEffect](
    host:     String,
    port:     Int,
    user:     String,
    database: String,
    check:    Boolean = true
  ): Resource[F, Session[F]] =
    for {
      s <- Session[F](host, port, check)
      _ <- Resource.liftF(s.protoSession.startup(user, database))
      // TODO: password negotiation, SASL, etc.
    } yield s

  /**
   * Resource yielding a new `Session` with the given `host`, `port`, and statement checking policy.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param check Check all `prepare` and `quick` statements for consistency with the schema. This
   *   is true by default and is recommended for development work. You may wish to turn this off in
   *   production but honestly it's really cheap and probably worth keeping.
   */
  private def apply[F[_]: ConcurrentEffect](
    host:  String,
    port:  Int,
    check: Boolean
  ): Resource[F, Session[F]] =
    ProtoSession[F](host, port, check).map(fromSession(_))

  def fromSession[F[_]: Sync](session: ProtoSession[F]): Session[F] =
    new Session[F](session) {

      def bind[A, B](pq: PreparedQuery[A, B], args: A): Resource[F, QueryPortal[B]] =
        Resource.make(protoSession.bind(pq, args))(protoSession.close)

      // we could probably cache these
      def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[A, B]] =
        Resource.make(protoSession.prepare(query))(protoSession.close)

      def stream[A, B](query: PreparedQuery[A, B], chunkSize: Int, args: A): Stream[F, B] =
        Stream.resource(bind(query, args)).flatMap { portal =>
          def chunks: Stream[F, B] =
            Stream.eval(protoSession.execute(portal, chunkSize)).flatMap { case (bs, more) =>
              val s = Stream.chunk(Chunk.seq(bs))
              if (more) s ++ chunks
              else s
            }
          chunks
        }

      def option[A, B](query: PreparedQuery[A, B], args: A): F[Option[B]] =
        bind(query, args).use { p =>
          execute(p, 1).flatMap {
            case bs ~ false => bs.headOption.pure[F]
            case _  ~ true  => Sync[F].raiseError(new RuntimeException("More than one value returned."))
          }
        }

      def first[A, B](query: PreparedQuery[A, B], args: A): F[B] =
        bind(query, args).use { p =>
          execute(p, 1).flatMap {
            case List(b) ~ false => b.pure[F]
            case Nil     ~ false => Sync[F].raiseError(new RuntimeException("Expected exactly one result, none returned."))
            case _               => Sync[F].raiseError(new RuntimeException("Expected exactly one result, more returned."))
          }
        }

      def listen(channel: Identifier, maxQueued: Int): Stream[F, Notification] =
        for {
          _ <- Stream.resource(Resource.make(protoSession.listen(channel))(_ => protoSession.unlisten(channel)))
          n <- protoSession.notifications(maxQueued).filter(_.channel === channel)
        } yield n

    }

}