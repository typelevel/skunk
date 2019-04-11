// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.effect.{ Concurrent, ContextShift, Resource }
import fs2.concurrent.Signal
import fs2.Stream
import skunk.{ Command, Query, Statement, ~, Void }
import skunk.data._
import skunk.net.message.RowDescription
import skunk.util.Origin

/**
 * Interface for a Postgres database, expressed through high-level operations that rely on exchange
 * of multiple messages. Operations here can be executed concurrently and are non-cancelable.
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
  def prepareCommand[A](command: Command[A]): Resource[F, Protocol.PreparedCommand[F, A]]

  /**
   * Prepare a query (a statement that produces rows), yielding a `Protocol.PreparedCommand` which
   * which will be closed after use.
   */
  def prepareQuery[A, B](query: Query[A, B]): Resource[F, Protocol.PreparedQuery[F, A, B]]

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

  // Protocol has its own internal representation for prepared statements and portals that expose
  // internals that aren't relevant to the end-user API. Specifically there are a lot of back-
  // pointers that aren't useful for end users but are necessary for context-aware error reporting.

  final case class StatementName(value: String)
  final case class PortalName(value: String)

  trait Managed[A] {
    def dbid: A
  }

  trait PreparedStatement[F[_], A] extends Managed[StatementName] {
    def statement: Statement[A]
  }

  abstract class PreparedCommand[F[_], A](
    val command: Command[A],
    val dbid:    StatementName,
  ) extends PreparedStatement[F, A] {
    def statement: Statement[A] = command
    def bind(args: A, argsOrigin: Origin): Resource[F, CommandPortal[F, A]]
  }

  abstract class PreparedQuery[F[_], A, B](
    val query:          Query[A, B],
    val dbid:           StatementName,
    val rowDescription: RowDescription
  ) extends PreparedStatement[F, A] {
    def statement: Statement[A] = query
    def bind(args: A, argsOrigin: Origin): Resource[F, QueryPortal[F, A, B]]
  }

  trait Portal[F[_], A] extends Managed[PortalName] {
    def preparedStatement: PreparedStatement[F, A]
  }

  abstract class CommandPortal[F[_], A](
    val dbid: PortalName
  ) extends Portal[F, A] {
    def preparedCommand: PreparedCommand[F, A]
    def preparedStatement: PreparedStatement[F, A] = preparedCommand
    def execute: F[Completion]
  }

  trait QueryPortal[F[_], A, B] extends Portal[F, A] {
    def preparedQuery: PreparedQuery[F, A, B]
    def preparedStatement: PreparedStatement[F, A] = preparedQuery
    def arguments: A
    def argumentsOrigin: Origin
    def execute(maxRows: Int): F[List[B] ~ Boolean]
  }

  /**
   * Resource yielding a new `Protocol` with the given `host` and `port`.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   */
  def apply[F[_]: Concurrent: ContextShift](
    host:  String,
    port:  Int     = 5432
  ): Resource[F, Protocol[F]] =
    for {
      bms    <- BufferedMessageSocket[F](host, port)
      atomic <- Resource.liftF(Atomic.fromBufferedMessageSocket(bms))
    } yield
      new Protocol[F] {

        def notifications(maxQueued: Int): Stream[F, Notification] =
          bms.notifications(maxQueued)

        def parameters: Signal[F, Map[String, String]] =
          bms.parameters

        def prepareCommand[A](command: Command[A]): Resource[F, PreparedCommand[F, A]] =
          for {
            dbid <- atomic.parse(command)
            _    <- Resource.liftF(atomic.checkCommand(command, dbid))
          } yield new PreparedCommand[F, A](command, dbid) { pc =>
            def bind(args: A, origin: Origin): Resource[F, CommandPortal[F, A]] =
              atomic.bind(this, args, origin).map { dbid =>
                new CommandPortal[F, A](dbid) {
                  val preparedCommand: PreparedCommand[F, A] = pc
                  val execute: F[Completion] = atomic.executeCommand(dbid)
                }
              }
          }

        def prepareQuery[A, B](query: Query[A, B]): Resource[F, PreparedQuery[F, A, B]] =
          for {
            dbid <- atomic.parse(query)
            rd   <- Resource.liftF(atomic.checkQuery(query, dbid))
          } yield new PreparedQuery[F, A, B](query, dbid, rd) { pq =>
            def bind(args: A, origin: Origin): Resource[F, QueryPortal[F, A, B]] =
              atomic.bind(this, args, origin).map { portal =>
                new QueryPortal[F, A, B] {
                  val dbid            = portal
                  val preparedQuery   = pq
                  val arguments       = args
                  val argumentsOrigin = origin
                  def execute(maxRows: Int): F[List[B] ~ Boolean] =
                    atomic.executeQuery(this, maxRows)
                }
              }
          }

        def quick(command: Command[Void]): F[Completion] =
          atomic.executeQuickCommand(command)

        def quick[B](query: Query[Void, B]): F[List[B]] =
          atomic.executeQuickQuery(query)

        def startup(user: String, database: String): F[Unit] =
          atomic.startup(user, database)

        def transactionStatus: Signal[F, TransactionStatus] =
          bms.transactionStatus

      }

}


