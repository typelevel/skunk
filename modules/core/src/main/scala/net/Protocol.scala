// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.effect.{ Concurrent, ContextShift, Resource }
import fs2.concurrent.Signal
import fs2.Stream
import skunk.{ Command, Query, Statement, ~, Void }
import skunk.data._
import skunk.util.{ Namer, Origin }
import scala.concurrent.duration.FiniteDuration
import skunk.util.Typer
import natchez.Trace
import fs2.io.tcp.SocketGroup
import skunk.net.protocol.Exchange

/**
 * Interface for a Postgres database, expressed through high-level operations that rely on exchange
 * of multiple messages. Operations here can be executed concurrently and are non-cancelable. The
 * structures returned here expose internals (safely) that are important for error reporting but are
 * not generally useful for end users.
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
   * Prepare a command (a statement that produces no rows), yielding a `Protocol.PreparedCommand`
   * which will be closed after use.
   */
  def prepare[A](command: Command[A], ty: Typer): Resource[F, Protocol.PreparedCommand[F, A]]

  /**
   * Prepare a query (a statement that produces rows), yielding a `Protocol.PreparedCommand` which
   * which will be closed after use.
   */
  def prepare[A, B](query: Query[A, B], ty: Typer): Resource[F, Protocol.PreparedQuery[F, A, B]]

  /**
   * Execute a non-parameterized command (a statement that produces no rows), yielding a
   * `Completion`. This is equivalent to `prepare` + `bind` + `execute` but it uses the "simple"
   * query protocol which requires fewer message exchanges.
   */
  def execute(command: Command[Void]): F[Completion]

  /**
   * Execute a non-parameterized query (a statement that produces rows), yielding all rows. This is
   * equivalent to `prepare` + `bind` + `execute` but it uses the "simple" query protocol which
   * requires fewer message exchanges. If you wish to page or stream results you need to use the
   * general protocol instead.
   */
  def execute[A](query: Query[Void, A], ty: Typer): F[List[A]]

  /**
   * Initiate the session. This must be the first thing you do. This is very basic at the moment.
   */
  def startup(user: String, database: String, password: Option[String]): F[Unit]

  /**
   * Signal representing the current transaction status as reported by `ReadyForQuery`. It's not
   * clear that this is a useful thing to expose.
   */
  def transactionStatus: Signal[F, TransactionStatus]

}

object Protocol {

  /**
   * Postgres identifier of a prepared statement, used by the protocol in subsequent `Bind`
   * operations.
   */
  final case class StatementId(value: String)

  /** Postgres identifier of a portal, used by the protocol in subsequent `Execute` operations. */
  final case class PortalId(value: String)

  /**
   * A prepared statement.
   * @param id the Postgres identifier of this statement.
   * @param statement the userland `Statement` used to construct this `PreparedStatement`.
   */
  sealed trait PreparedStatement[F[_], A] {
    def id:        StatementId
    def statement: Statement[A]
  }

  /**
   * A prepared command.
   * @param id the Postgres identifier of this statement.
   * @param command the userland `Command` used to construct this `PreparedCommand`.
   */
  abstract class PreparedCommand[F[_], A](
    val id:      StatementId,
    val command: Command[A],
  ) extends PreparedStatement[F, A] {
    def statement: Statement[A] = command
    def bind(args: A, argsOrigin: Origin): Resource[F, CommandPortal[F, A]]
  }

  /**
   * A prepared query.
   * @param id the Postgres identifier of this statement.
   * @param query the userland `Query` used to construct this `PreparedQuery`.
   * @param rowDescription a `RowDescription` specifying this `PreparedQuery`'s output format.'.
   */
  abstract class PreparedQuery[F[_], A, B](
    val id:             StatementId,
    val query:          Query[A, B],
    val rowDescription: TypedRowDescription
  ) extends PreparedStatement[F, A] {
    def statement: Statement[A] = query
    def bind(args: A, argsOrigin: Origin): Resource[F, QueryPortal[F, A, B]]
  }

  /**
   * @param id the Postgres identifier of this statement.
   * @param preparedStatement the `PreparedStatement` used to construct this `Portal`.
   */
  sealed trait Portal[F[_], A] {
    def id:             PortalId
    def preparedStatement: PreparedStatement[F, A]
    def arguments:       A
    def argumentsOrigin: Origin
  }

  abstract class CommandPortal[F[_], A](
    val id:              PortalId,
    val preparedCommand: PreparedCommand[F, A],
    val arguments:       A,
    val argumentsOrigin: Origin,
  ) extends Portal[F, A] {
    def preparedStatement: PreparedStatement[F, A] = preparedCommand
    def execute: F[Completion]
  }

  abstract class QueryPortal[F[_], A, B](
    val id:              PortalId,
    val preparedQuery:   PreparedQuery[F, A, B],
    val arguments:       A,
    val argumentsOrigin: Origin,
  ) extends Portal[F, A] {
    def preparedStatement: PreparedStatement[F, A] = preparedQuery
    def execute(maxRows: Int): F[List[B] ~ Boolean]
  }

  /**
   * Resource yielding a new `Protocol` with the given `host` and `port`.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   */
  def apply[F[_]: Concurrent: ContextShift: Trace](
    host:         String,
    port:         Int,
    debug:        Boolean,
    nam:          Namer[F],
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration,
    sg:           SocketGroup,
    sslOptions:   Option[SSLNegotiation.Options[F]],
  ): Resource[F, Protocol[F]] =
    for {
      bms <- BufferedMessageSocket[F](host, port, 256, debug, readTimeout, writeTimeout, sg, sslOptions) // TODO: should we expose the queue size?
      ex  <- Resource.liftF(Exchange[F])
    } yield
      new Protocol[F] {

        // Not super sure about this but it does make the sub-protocol implementations cleaner.
        // We'll see how well it works out.
        implicit val ms: MessageSocket[F] = bms
        implicit val na: Namer[F] = nam
        implicit val ExchangeF: protocol.Exchange[F] = ex

        override def notifications(maxQueued: Int): Stream[F, Notification] =
          bms.notifications(maxQueued)

        override def parameters: Signal[F, Map[String, String]] =
          bms.parameters

        override def prepare[A](command: Command[A], ty: Typer): Resource[F, PreparedCommand[F, A]] =
          protocol.Prepare[F].apply(command, ty)

        override def prepare[A, B](query: Query[A, B], ty: Typer): Resource[F, PreparedQuery[F, A, B]] =
          protocol.Prepare[F].apply(query, ty)

        override def execute(command: Command[Void]): F[Completion] =
          protocol.Query[F].apply(command)

        override def execute[B](query: Query[Void, B], ty: Typer): F[List[B]] =
          protocol.Query[F].apply(query, ty)

        override def startup(user: String, database: String, password: Option[String]): F[Unit] =
          protocol.Startup[F].apply(user, database, password)

        override def transactionStatus: Signal[F, TransactionStatus] =
          bms.transactionStatus

      }

}


