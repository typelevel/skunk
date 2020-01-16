// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.effect._
import cats.implicits._
import fs2.concurrent.Signal
import fs2.io.tcp.SocketGroup
import fs2.Pipe
import fs2.Stream
import natchez.Trace
import scala.concurrent.duration._
import skunk.codec.all.bool
import skunk.data._
import skunk.net.Protocol
import skunk.util._
import skunk.util.Typer.Strategy.{ BuiltinsOnly, SearchPath }

/**
 * Represents a live connection to a Postgres database. Operations provided here are safe to use
 * concurrently. Note that this is a lifetime-managed resource and as such is invalid outside the
 * scope of its owning `Resource`, as are any streams constructed here. If you `start` an operation
 * be sure to `join` its `Fiber` before releasing the resource.
 *
 * See the [[skunk.Session companion object]] for information on obtaining a pooled or single-use
 * instance.
 *
 * @groupprio Queries 10
 * @groupdesc Queries A query is any SQL statement that returns rows; i.e., any `SELECT` or
 * `VALUES` query, or an `INSERT`, `UPDATE`, or `DELETE` command that returns rows via `RETURNING`.
 *  Parameterized queries must first be prepared, then can be executed many times with different
 *  arguments. Non-parameterized queries can be executed directly.

 * @groupprio Commands 20
 * @groupdesc Commands A command is any SQL statement that cannot return rows. Parameterized
 *   commands must first be prepared, then can be executed many times with different arguments.
 *   Commands without parameters can be executed directly.
 *
 * @groupprio Transactions 25
 * @groupdesc Transactions Users can manipulate transactions directly via commands like `BEGIN` and
 *   `COMMIT`, but dealing with cancellation and error conditions can be complicated and repetitive.
 *   Skunk provides managed transaction blocks to make this easier.
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
   * Execute a non-parameterized query and yield all results. If you have parameters or wish to limit
   * returned rows use `prepare` instead.
   * @group Queries
   */
  def execute[A](query: Query[Void, A]): F[List[A]]

  /**
   * Execute a non-parameterized query and yield exactly one row, raising an exception if there are
   * more or fewer. If you have parameters use `prepare` instead.
   * @group Queries
   */
  def unique[A](query: Query[Void, A]): F[A]

  /**
   * Execute a non-parameterized query and yield at most one row, raising an exception if there are
   * more. If you have parameters use `prepare` instead.
   * @group Queries
   */
  def option[A](query: Query[Void, A]): F[Option[A]]

  /**
   * Execute a non-parameterized command and yield a `Completion`. If you have parameters use
   * `prepare` instead.
   * @group Commands
   */
  def execute(command: Command[Void]): F[Completion]

  /**
   * Resource that prepares a query, yielding a `PreparedQuery` which can be executed multiple
   * times with different arguments.
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
   * Transform a `Command` into a `Pipe` from inputs to `Completion`s.
   * @group Commands
   */
  def pipe[A](command: Command[A]): Pipe[F, A, Completion] = fa =>
    Stream.resource(prepare(command)).flatMap(pc => fa.evalMap(pc.execute)).scope

  /**
   * A named asynchronous channel that can be used for inter-process communication.
   * @group Channels
   */
  def channel(name: Identifier): Channel[F, String, Notification]

  /**
   * Resource that wraps a transaction block. A transaction is begun before entering the `use`
   * block, on success the block is executed, and on exit the following behavior holds.
   *
   *   - If the block exits normally, and the session transaction status is
   *     - `Active`, then the transaction will be committed.
   *     - `Idle`, then this means the user terminated the
   *       transaction explicitly inside the block and there is nothing to be done.
   *     - `Error` then this means the user encountered and
   *       handled an error but left the transaction in a failed state, and the transaction will
   *       be rolled back.
   *   - If the block exits due to cancellation or an error and the session transaction status is
   *     not `Idle` then the transaction will be rolled back and any error will be re-raised.
   * @group Transactions
   */
  def transaction[A]: Resource[F, Transaction[F]]

  def typer: Typer

}



/** @group Companions */
object Session {

  object Recyclers {

    /**
     * Ensure the session is idle, then remove all channel listeners and reset all variables to
     * system defaults. Note that this is usually more work than you need to do. If your application
     * isn't running arbitrary statements then `minimal` might be more efficient.
     */
    def full[F[_]: Monad]: Recycler[F, Session[F]] =
      ensureIdle[F] <+> unlistenAll <+> resetAll

    /**
     * Ensure the session is idle, then run a trivial query to ensure the connection is in working
     * order. In most cases this check is sufficient.
     */
    def minimal[F[_]: Monad]: Recycler[F, Session[F]] =
      ensureIdle[F] <+> Recycler(_.unique(Query("VALUES (true)", Origin.unknown, Void.codec, bool)))

    /**
     * Yield `true` the session is idle (i.e., that there is no ongoing transaction), otherwise
     * yield false. This check does not require network IO.
     */
    def ensureIdle[F[_]: Monad]: Recycler[F, Session[F]] =
      Recycler(_.transactionStatus.get.map(_ == TransactionStatus.Idle))

    /** Remove all channel listeners and yield `true`. */
    def unlistenAll[F[_]: Functor]: Recycler[F, Session[F]] =
      Recycler(_.execute(Command("UNLISTEN *", Origin.unknown, Void.codec)).as(true))

    /** Reset all variables to system defaults and yield `true`. */
    def resetAll[F[_]: Functor]: Recycler[F, Session[F]] =
      Recycler(_.execute(Command("RESET ALL", Origin.unknown, Void.codec)).as(true))

  }

  /**
   * Resource yielding a `SessionPool` managing up to `max` concurrent `Session`s. Typically you
   * will `use` this resource once on application startup and pass the resulting
   * `Resource[F, Session[F]]` to the rest of your program.
   *
   * Note that calling `.flatten` on the nested `Resource` returned by this method may seem
   * reasonable, but it will result in a resource that allocates a new pool for each session, which
   * is probably not what you want.
   * @param host          Postgres server host
   * @param port          Postgres port, default 5432
   * @param user          Postgres user
   * @param database      Postgres database
   * @param max           Maximum concurrent sessions
   * @param debug
   * @param readTimeout
   * @param writeTimeout
   * @param strategy
   * @group Constructors
   */
  def pooled[F[_]: Concurrent: ContextShift: Trace](
    host:         String,
    port:         Int            = 5432,
    user:         String,
    database:     String,
    password:     Option[String] = none,
    max:          Int,
    debug:        Boolean        = false,
    readTimeout:  FiniteDuration = Int.MaxValue.seconds,
    writeTimeout: FiniteDuration = 5.seconds,
    strategy:     Typer.Strategy = Typer.Strategy.BuiltinsOnly
  ): Resource[F, Resource[F, Session[F]]] = {

    def session(socketGroup:  SocketGroup): Resource[F, Session[F]] =
      fromSocketGroup[F](socketGroup, host, port, user, database, password, debug, readTimeout, writeTimeout, strategy)

    for {
      blocker <- Blocker[F]
      sockGrp <- SocketGroup[F](blocker)
      pool    <- Pool.of(session(sockGrp), max)(Recyclers.full)
    } yield pool

  }

  /**
   * Resource yielding logically unpooled sessions. This can be convenient for demonstrations and
   * programs that only need a single session. In reality each session is managed by its own
   * single-session pool. This method is shorthand for `Session.pooled(..., max = 1, ...).flatten`.
   * @see pooled
   */
  def single[F[_]: Concurrent: ContextShift: Trace](
    host:         String,
    port:         Int            = 5432,
    user:         String,
    database:     String,
    password:     Option[String] = none,
    debug:        Boolean        = false,
    readTimeout:  FiniteDuration = Int.MaxValue.seconds,
    writeTimeout: FiniteDuration = 5.seconds,
    strategy:     Typer.Strategy = Typer.Strategy.BuiltinsOnly
  ): Resource[F, Session[F]] =
    pooled(
      host         = host,
      port         = port,
      user         = user,
      database     = database,
      password     = password,
      max          = 1,
      debug        = debug,
      readTimeout  = readTimeout,
      writeTimeout = writeTimeout,
      strategy     = strategy
    ).flatten


  def fromSocketGroup[F[_]: Concurrent: ContextShift: Trace](
    socketGroup:  SocketGroup,
    host:         String,
    port:         Int            = 5432,
    user:         String,
    database:     String,
    password:     Option[String] = none,
    debug:        Boolean        = false,
    readTimeout:  FiniteDuration = Int.MaxValue.seconds,
    writeTimeout: FiniteDuration = 5.seconds,
    strategy:     Typer.Strategy = Typer.Strategy.BuiltinsOnly
  ): Resource[F, Session[F]] =
    for {
      namer <- Resource.liftF(Namer[F])
      proto <- Protocol[F](host, port, debug, namer, readTimeout, writeTimeout, socketGroup)
      _     <- Resource.liftF(proto.startup(user, database, password))
      sess  <- Resource.liftF(fromProtocol(proto, namer, strategy))
    } yield sess

  /**
   * Construct a `Session` by wrapping an existing `Protocol`, which we assume has already been
   * started up.
   * @group Constructors
   */
  def fromProtocol[F[_]: Sync](
    proto:    Protocol[F],
    namer:    Namer[F],
    strategy: Typer.Strategy
  ): F[Session[F]] = {

    val ft: F[Typer] =
      strategy match {
        case BuiltinsOnly => Typer.Static.pure[F]
        case SearchPath   => Typer.fromProtocol(proto)
      }

    ft.map { typ =>
      new Session[F] {

        override val typer: Typer = typ

        override def execute(command: Command[Void]): F[Completion] =
          proto.execute(command)

        override def channel(name: Identifier): Channel[F, String, Notification] =
          Channel.fromNameAndProtocol(name, proto)

        override def parameters: Signal[F, Map[String, String]] =
          proto.parameters

        override def parameter(key: String): Stream[F, String] =
          parameters.discrete.map(_.get(key)).unNone.changes

        override def transactionStatus: Signal[F, TransactionStatus] =
          proto.transactionStatus

        override def execute[A](query: Query[Void, A]): F[List[A]] =
          proto.execute(query, typer)

        override def unique[A](query: Query[Void, A]): F[A] =
          execute(query).flatMap {
            case a :: Nil => a.pure[F]
            case Nil      => Sync[F].raiseError(new RuntimeException("Expected exactly one row, none returned."))
            case _        => Sync[F].raiseError(new RuntimeException("Expected exactly one row, more returned."))
          }

        override def option[A](query: Query[Void, A]): F[Option[A]] =
          execute(query).flatMap {
            case a :: Nil => a.some.pure[F]
            case Nil      => none[A].pure[F]
            case _        => Sync[F].raiseError(new RuntimeException("Expected at most one row, more returned."))
          }

        override def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[F, A, B]] =
          proto.prepare(query, typer).map(PreparedQuery.fromProto(_))

        override def prepare[A](command: Command[A]): Resource[F, PreparedCommand[F, A]] =
          proto.prepare(command, typer).map(PreparedCommand.fromProto(_))

        override def transaction[A]: Resource[F, Transaction[F]] =
          Transaction.fromSession(this, namer)

      }
    }
  }

  // TODO: upstream
  implicit class SignalOps[F[_], A](outer: Signal[F, A]) {
    def mapK[G[_]](fk: F ~> G): Signal[G, A] =
      new Signal[G, A] {
        def continuous: Stream[G,A] = outer.continuous.translate(fk)
        def discrete: Stream[G,A] = outer.continuous.translate(fk)
        def get: G[A] = fk(outer.get)
      }
  }

  implicit class SessionSyntax[F[_]](outer: Session[F]) {

    /**
     * Transform this `Session` by a given `FunctionK`.
     * @group Transformations
     */
    def mapK[G[_]: Applicative: Defer](fk: F ~> G)(
      implicit ev: Bracket[F, Throwable]
    ): Session[G] =
      new Session[G] {
        override val typer: Typer = outer.typer
        override def channel(name: Identifier): Channel[G,String,Notification] = outer.channel(name).mapK(fk)
        override def execute(command: Command[Void]): G[Completion] = fk(outer.execute(command))
        override def execute[A](query: Query[Void,A]): G[List[A]] = fk(outer.execute(query))
        override def option[A](query: Query[Void,A]): G[Option[A]] = fk(outer.option(query))
        override def parameter(key: String): Stream[G,String] = outer.parameter(key).translate(fk)
        override def parameters: Signal[G,Map[String,String]] = outer.parameters.mapK(fk)
        override def prepare[A, B](query: Query[A,B]): Resource[G,PreparedQuery[G,A,B]] = outer.prepare(query).mapK(fk).map(_.mapK(fk))
        override def prepare[A](command: Command[A]): Resource[G,PreparedCommand[G,A]] = outer.prepare(command).mapK(fk).map(_.mapK(fk))
        override def transaction[A]: Resource[G,Transaction[G]] = outer.transaction[A].mapK(fk).map(_.mapK(fk))
        override def transactionStatus: Signal[G,TransactionStatus] = outer.transactionStatus.mapK(fk)
        override def unique[A](query: Query[Void,A]): G[A] = fk(outer.unique(query))
      }
  }

}
