package skunk

import cats.effect._
import cats.implicits._
import fs2.{ Chunk, Stream }
import fs2.concurrent.Signal
import skunk.data._
import skunk.net.ProtoSession
import skunk.util.Pool

/**
 * Represents a live connection to a Postgres database. Operations provided here are safe to use
 * concurrently. Note that this is a lifetime-managed resource and as such is invalid outside the
 * scope of its owning `Resource`, as are any streams constructed here. If you `start` an operation
 * be sure to `join` its `Fiber` before releasing the resource.
 *
 * ==Obtaining an Instance==
 * See the [[skunk.Session$ companion object]] for information on obtaining a pooled or single-use
 * instance.
 *
 * @groupname Notifications Asynchronous Channel Notifications
 * @groupdesc Notifications Here is the description.
 * @groupprio Notifications -10
 */
trait Session[F[_]] {

  val protoSession: ProtoSession[F]

  /**
   * A prepared query, executable on this `Session` only.
   * @group Queries
   */
  type PreparedQuery[A, B] = protoSession.PreparedQuery[A, B]

  /**
   * A cursor from which rows can be fetched, usable on this `Session` only.
   * @group Queries
   */
  type Cursor[A] = protoSession.QueryPortal[A]

  /**
   * Signal broadcasting changes to the session configuration.
   * @group Session Environment
   */
  def parameters: Signal[F, Map[String, String]]

  /**
   * Signal broadcasting changes for a single configuration key.
   * @group Session Environment
   */
  def parameter(key: String): Stream[F, String]

  /**
   * Signal representing the current transaction status.
   * @group Session Environment
   */
  def transactionStatus: Signal[F, TransactionStatus]

  /**
   * Excute a non-parameterized query and yield all results. If you have parameters or wish to limit
   * returned rows use `bind`/`execute` or `stream`.
   * @group Queries
   */
  def quick[A](query: Query[Void, A]): F[List[A]]

  /**
   * Prepare a `Query` for execution by parsing its SQL statement. The resulting `PreparedQuery` can
   * be executed multiple times with different arguments.
   * @group Queries
   */
  def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[A, B]]

  /**
   * Check that a `PreparedQuery`'s asserted argument and result types align correctly with the
   * schema. In case of misalignment an exception is raised with a description of the problem.
   * @group Queries
   */
  def check[A, B](query: PreparedQuery[A, B]): F[Unit]

  /**
   * Resource that binds the supplied arguments to a `PreparedQuery`, yielding a `Cursor` from which
   * rows can be `fetch`ed. Note that higher-level operations like `stream`, `option`, and `unique`
   * are usually what you want.
   * @group Queries
   */
  def open[A, B](pq: PreparedQuery[A, B], args: A = Void): Resource[F, Cursor[B]]

  /**
   * Fetch the next `maxRows` from `cursor`, yielding a list of values and a boolean, `true` if
   * more rows are available, `false` otherwise.
   * @group Queries
   */
  def fetch[A](cursor: Cursor[A], maxRows: Int): F[(List[A], Boolean)]

  /**
   * Construct a stream that calls `fetch` repeatedly and emits chunks until none remain. Note
   * that each chunk is read atomically while holding the session mutex, which means interleaved
   * streams will achieve better fairness with smaller chunks but greater overall throughput with
   * larger chunks. So it's important to consider the use case when specifying `chunkSize`.
   * @group Queries
   */
  def stream[A, B](query: PreparedQuery[A, B], chunkSize: Int, args: A = Void): Stream[F, B]

  /**
   * Fetch and return at most one row, raising an exception if more rows are available.
   * @group Queries
   */
  def option[A, B](query: PreparedQuery[A, B], args: A): F[Option[B]]

  /**
   * Fetch and return exactly one row, raising an exception if there are more or fewer.
   * @group Queries
   */
  def unique[A, B](query: PreparedQuery[A, B], args: A): F[B]
  def unique[B](query: PreparedQuery[Void, B]): F[B] = unique(query, Void)

  def unique[A, B](query: Query[A, B], args: A)(
    implicit ev: Sync[F]
  ): F[B] =
    prepare(query).use(unique(_, args))

  def unique[B](query: Query[Void, B])(
    implicit ev: Sync[F]
  ): F[B] =
    prepare(query).use(unique(_, Void))


  /**
   * Excute a non-parameterized command and yield a `Completion`. If you have parameters use
   * `execute`.
   * @group Commands
   */
  def quick(command: Command[Void]): F[Completion]

  /**
   * A prepared command, executable on this `Session` only.
   * @group Commands
   */
  type PreparedCommand[A] = protoSession.PreparedCommand[A]

  /**
   * Prepare a statement that returns no rows.
   * @group Commands
   */
  def prepare[A](command: Command[A]): Resource[F, PreparedCommand[A]]

  /**
   * @group Commands
   */
  def check[A](command: PreparedCommand[A]): F[Unit]

  /**
   * @group Commands
   */
  def execute[A](pq: PreparedCommand[A], args: A = Void): F[Completion]

  /**
   * Construct a `Stream` that subscribes to notifications for `channel`, emits any notifications
   * that arrive (this can happen at any time), then unsubscribes when the stream is terminated.
   * Note that once such a stream is started it is important to consume all notifications as quickly
   * as possible to avoid blocking message processing for other operations on the `Session`
   * (although typically a dedicated `Session` will receive channel notifications so this won't be
   * an issue).
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def listen(channel: Identifier, maxQueued: Int): Stream[F, Notification]

  /**
   * Send a notification on the given channel.
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def notify(channel: Identifier, message: String): F[Unit]

}



object Session {


  /**
   * Resource yielding a `SessionPool` managing up to `max` concurrent `Session`s.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param user
   * @param database
   * @param max
   * @param check Check all `prepare` and `quick` statements for consistency with the schema. This
   *   is true by default and is recommended for development work. You may wish to turn this off in
   *   production but honestly it's really cheap and probably worth keeping.
   * @group Constructors
   */
  def pooled[F[_]: ConcurrentEffect](
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

    Pool.of(single(host, port, user, database, check), max, reset)

  }

  /**
   * Resource yielding a new, unpooled `Session` with the given connect information and statement
   * checking policy.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param user
   * @param database
   * @param check Check all `prepare` and `quick` statements for consistency with the schema. This
   *   is true by default and is recommended for development work. You may wish to turn this off in
   *   production but honestly it's really cheap and probably worth keeping.
   * @group Constructors
   */
  def single[F[_]: ConcurrentEffect](
    host:     String,
    port:     Int,
    user:     String,
    database: String,
    check:    Boolean = true
  ): Resource[F, Session[F]] =
    for {
      ps <- ProtoSession[F](host, port, check)
      _  <- Resource.liftF(ps.startup(user, database))
      // TODO: password negotiation, SASL, etc.
    } yield fromProtoSession(ps)

  /**
   * Construct a `Session` by wrapping an existing `ProtoSession`, which we assume has already been
   * started up. This should probably be pushed down a layer.
   * @group Constructors
   */
  def fromProtoSession[F[_]: Sync](ps: ProtoSession[F]): Session[F] =
    new Session[F] {

      val protoSession = ps

      // Trivial delegates
      def quick(command: Command[Void]) = protoSession.quick(command)
      def check[A](command: PreparedCommand[A]) = protoSession.check(command)
      def notify(channel: Identifier, message: String) = protoSession.notify(channel, message)
      def check[A, B](query: PreparedQuery[A, B]) = protoSession.check(query)
      def fetch[A](cursor: Cursor[A], maxRows: Int) = protoSession.execute(cursor, maxRows)
      def parameters = protoSession.parameters
      def parameter(key: String) = parameters.discrete.map(_.get(key)).unNone.changes
      def transactionStatus = protoSession.transactionStatus
      def quick[A](query: Query[Void, A]) = protoSession.quick(query)

      def open[A, B](pq: PreparedQuery[A, B], args: A): Resource[F, Cursor[B]] =
        Resource.make(protoSession.bind(pq, args))(protoSession.close)

      def execute[A](pq: PreparedCommand[A], args: A): F[Completion] =
        Resource.make(protoSession.bind(pq, args))(protoSession.close).use(protoSession.execute)

      def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[A, B]] =
        Resource.make(protoSession.prepare(query))(protoSession.close)

      def prepare[A](command: Command[A]): Resource[F, PreparedCommand[A]] =
        Resource.make(protoSession.prepare(command))(protoSession.close)

     def stream[A, B](query: PreparedQuery[A, B], chunkSize: Int, args: A = Void): Stream[F, B] =
        Stream.resource(open(query, args)).flatMap { cursor =>
          def chunks: Stream[F, B] =
            Stream.eval(protoSession.execute(cursor, chunkSize)).flatMap { case (bs, more) =>
              val s = Stream.chunk(Chunk.seq(bs))
              if (more) s ++ chunks
              else s
            }
          chunks
        }

      def option[A, B](query: PreparedQuery[A, B], args: A): F[Option[B]] =
        open(query, args).use { p =>
          fetch(p, 2).flatMap { case (bs, _) =>
            bs match {
              case b :: Nil => b.some.pure[F]
              case Nil      => none[B].pure[F]
              case _        => Sync[F].raiseError(new RuntimeException("Expected exactly one result, more returned."))
            }
          }
        }

      def unique[A, B](query: PreparedQuery[A, B], args: A): F[B] =
        open(query, args).use { p =>
          fetch(p, 2).flatMap { case (bs, _) =>
            bs match {
              case b :: Nil => b.pure[F]
              case Nil      => Sync[F].raiseError(new RuntimeException("Expected exactly one result, none returned."))
              case _        => Sync[F].raiseError(new RuntimeException("Expected exactly one result, more returned."))
            }
          }
        }

      def listen(channel: Identifier, maxQueued: Int): Stream[F, Notification] =
        for {
          _ <- Stream.resource(Resource.make(protoSession.listen(channel))(_ => protoSession.unlisten(channel)))
          n <- protoSession.notifications(maxQueued).filter(_.channel === channel)
        } yield n

    }

}



