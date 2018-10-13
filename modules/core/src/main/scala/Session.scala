package skunk

import cats.effect._
import cats.implicits._
import fs2.{ Chunk, Stream }
import fs2.concurrent.Signal
import skunk.message.{ CommandComplete, ReadyForQuery, NotificationResponse }

/**
 * Refinement of `ProtoSession` that exposes lifetime-managed objects as resources and streams that can
 * safely be used concurrently, as long as the session is active.
 */
abstract class Session[F[_]](val s: ProtoSession[F]) {

  // Direct Delegates

  type PreparedQuery[A, B] = s.PreparedQuery[A, B]
  type PreparedCommand[A]  = s.PreparedCommand[A]
  type Portal[A]           = s.Portal[A]

  def startup(user: String, database: String): F[Unit] = s.startup(user, database)
  def parameters: Signal[F, Map[String, String]] = s.parameters
  def transactionStatus: Signal[F, ReadyForQuery.Status] = s.transactionStatus
  def notify(channel: Identifier, message: String): F[Unit] = s.notify(channel, message)
  def quick[A: ProtoSession.NonParameterized, B](query: Query[A, B]): F[List[B]] = s.quick(query)
  def quick[A: ProtoSession.NonParameterized](command: Command[A]): F[CommandComplete] = s.quick(command)
  def execute[A](portal: Portal[A], maxRows: Int): F[List[A] ~ Boolean] = s.execute(portal, maxRows)
  def check[A, B](query: PreparedQuery[A, B]): F[Unit] = s.check(query)
  def check[A](command: PreparedCommand[A]): F[Unit] = s.check(command)

  // Trivial derivations

  def parameter(key: String): Stream[F, String] =
    parameters.discrete.map(_.get(key)).unNone.changes

  // Abstract members

  def bind[A, B](pq: PreparedQuery[A, B], args: A): Resource[F, s.Portal[B]]
  def prepare[A, B](query: Query[A, B]): Resource[F, s.PreparedQuery[A, B]]
  def listen(channel: Identifier, maxQueued: Int): Stream[F, NotificationResponse]

  /**
   * Stream that calls `execute` repeatedly and emits chunks until none remain. Note that each
   * chunk is read atomically while holding the session mutex, which means interleaved streams
   * will achieve better fairness with smaller chunks but greater overall throughput with larger
   * chunks. So it's important to consider the use case when specifying `chunkSize`.
   */
  def stream[A, B](query: PreparedQuery[A, B], args: A, chunkSize: Int): Stream[F, B]

  def option[A, B](query: PreparedQuery[A, B], args: A): F[Option[B]]

  def first[A, B](query: PreparedQuery[A, B], args: A): F[B]

}

object Session extends ToStringOps {

  def pool[F[_]: ConcurrentEffect](
    host:     String,
    port:     Int,
    user:     String,
    database: String,
    max:      Long,
    check:    Boolean = true
  ): Resource[F, Resource[F, Session[F]]] = {

    val connection: Resource[F, Session[F]] =
      for {
        s <- Session[F](host, port, check)
        _ <- Resource.liftF(s.startup(user, database))
      } yield s

    val reset: Session[F] => F[Boolean] = s =>
      for {
        // todo: unsubscribe all
        // todo: sync, rollback if necessary
        _ <- s.quick(sql"RESET ALL".command)
      } yield true

    Pool.of(connection, max, reset)

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
    ProtoSession[F](host, port, check).map(fromSession(_))

  def fromSession[F[_]: Sync](session: ProtoSession[F]): Session[F] =
    new Session[F](session) {

      def bind[A, B](pq: PreparedQuery[A, B], args: A): Resource[F, Portal[B]] =
        Resource.make(s.bind(pq, args))(s.close)

      // we could probably cache these
      def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[A, B]] =
        Resource.make(s.prepare(query))(s.close)

      def stream[A, B](query: PreparedQuery[A, B], args: A, chunkSize: Int): Stream[F, B] =
        Stream.resource(bind(query, args)).flatMap { portal =>
          def chunks: Stream[F, B] =
            Stream.eval(s.execute(portal, chunkSize)).flatMap { case (bs, more) =>
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

      def listen(channel: Identifier, maxQueued: Int): Stream[F, NotificationResponse] =
        for {
          _ <- Stream.resource(Resource.make(s.listen(channel))(_ => s.unlisten(channel)))
          n <- s.notifications(maxQueued).filter(_.channel === channel)
        } yield n

    }

}