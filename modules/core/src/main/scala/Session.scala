package skunk

import cats.effect._
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Signal
import skunk.data._
import skunk.proto.Protocol
import skunk.util.Pool

/**
 * Represents a live connection to a Postgres database. Operations provided here are safe to use
 * concurrently. Note that this is a lifetime-managed resource and as such is invalid outside the
 * scope of its owning `Resource`, as are any streams constructed here. If you `start` an operation
 * be sure to `join` its `Fiber` before releasing the resource.
 *
 * See the [[skunk.Session$ companion object]] for information on obtaining a pooled or single-use
 * instance.
 *
 * @groupprio Queries 10
 * @groupdesc Queries A query is any SQL statement that can returns rows. Parameterized
 *   queries must first be prepared, then can be executed many times with different arguments.
 *   Queries without parameters can be executed directly.

 * @groupprio Commands 20
 * @groupdesc Commands A command is any SQL statement that cannot return rows. Parameterized
 *   commands must first be prepared, then can be executed many times with different arguments.
 *   Commands without parameters can be executed directly.
 *
 * @groupprio Channels 30
 *
 * @groupprio Environment 30
 * @groupname Environment Session Environment
 * @groupdesc Environment The Postgres session has a dynamic environment that includes a
 *   configuration map with keys like `TimeZone` and `server_version`, as well as a current
 *   `TransactionStatus`. These can change asynchronously and are exposed as `Signal`s. Note that
 *   any `Stream` based on these signals is only valid for the lifetime of the `Session`.
 *
 * @group Session
 */
trait Session[F[_]] {

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
   * @group Environment
   */
  def parameters: Signal[F, Map[String, String]]

  /**
   * Stream (possibly empty) of discrete values for the specified key, via `parameters`.
   * @group Environment
   */
  def parameter(key: String): Stream[F, String]

  /**
   * Signal representing the current transaction status.
   * @group Environment
   */
  def transactionStatus: Signal[F, TransactionStatus]

  /**
   * Excute a non-parameterized query and yield all results. If you have parameters or wish to limit
   * returned rows use `bind`/`execute` or `stream`.
   * @group Queries
   */
  def execute[A](query: Query[Void, A]): F[List[A]]

  /**
   * Excute a non-parameterized command and yield a `Completion`. If you have parameters use
   * `execute`.
   * @group Commands
   */
  def execute(command: Command[Void]): F[Completion]

  /**
   * Prepare a `SELECT` or `VALUES` query; or an `INSERT`, `UPDATE`, or `DELETE` command that
   * returns rows via `RETURNING`. The resulting `PreparedQuery` can be executed multiple times with
   * different arguments.
   * @group Queries
   */
  def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[F, A, B]]

  /**
   * Prepare an `INSERT`, `UPDATE`, or `DELETE` command that returns no rows. The resulting
   * `PreparedCommand` can be executed multiple times with different arguments.
   * @group Commands
   */
  def prepare[A](command: Command[A]): Resource[F, PreparedCommand[F, A]]

  /**
   * A named asynchronous channel that can be used for inter-process communication.
   * @group Channels
   */
  def channel(name: Identifier): Channel[F, String, Notification]

}



/** @group Companions */
object Session {

  /**
   * Resource yielding a `SessionPool` managing up to `max` concurrent `Session`s.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param user
   * @param database
   * @param max
   * @param check Check all `prepare` and `execute` statements for consistency with the schema. This
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
        _ <- s.execute(Command("RESET ALL", Void.codec))
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
   * @param check Check all `prepare` and `execute` statements for consistency with the schema. This
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
      ps <- Protocol[F](host, port, check)
      _  <- Resource.liftF(ps.startup(user, database))
      // TODO: password negotiation, SASL, etc.
    } yield fromProtocol(ps)

  /**
   * Construct a `Session` by wrapping an existing `Protocol`, which we assume has already been
   * started up.
   * @group Constructors
   */
  def fromProtocol[F[_]: Sync](proto: Protocol[F]): Session[F] =
    new Session[F] {

      def execute(command: Command[Void]) =
        proto.quick(command)

      def channel(name: Identifier) =
        Channel.fromNameAndProtocol(name, proto)

      def parameters =
        proto.parameters

      def parameter(key: String) =
        parameters.discrete.map(_.get(key)).unNone.changes

      def transactionStatus =
        proto.transactionStatus

      def execute[A](query: Query[Void, A]) =
        proto.quick(query)

      def prepare[A, B](query: Query[A, B]) =
        Resource.make(proto.prepareQuery(query))(_.close).map(PreparedQuery.fromProto(_))

      def prepare[A](command: Command[A]) =
        Resource.make(proto.prepareCommand(command))(_.close).map(PreparedCommand.fromProto(_))

    }

}



