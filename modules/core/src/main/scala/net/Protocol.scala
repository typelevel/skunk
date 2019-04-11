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
  def prepare[A](command: Command[A]): Resource[F, Protocol.PreparedCommand[F, A]]

  /**
   * Prepare a query (a statement that produces rows), yielding a `Protocol.PreparedCommand` which
   * which will be closed after use.
   */
  def prepare[A, B](query: Query[A, B]): Resource[F, Protocol.PreparedQuery[F, A, B]]

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
  def execute[A](query: Query[Void, A]): F[List[A]]

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

  final case class StatementId(value: String)
  final case class PortalId(value: String)

  sealed abstract class Managed[A](val id: A)

  sealed abstract class PreparedStatement[F[_], A](
        id:        StatementId,
    val statement: Statement[A]
  ) extends Managed[StatementId](id)

  sealed abstract class PreparedCommand[F[_], A](
        id:      StatementId,
    val command: Command[A],
  ) extends PreparedStatement[F, A](id, command) {
    def bind(args: A, argsOrigin: Origin): Resource[F, CommandPortal[F, A]]
  }

  sealed abstract class PreparedQuery[F[_], A, B](
        id:             StatementId,
    val query:          Query[A, B],
    val rowDescription: RowDescription
  ) extends PreparedStatement[F, A](id, query) {
    def bind(args: A, argsOrigin: Origin): Resource[F, QueryPortal[F, A, B]]
  }

  sealed abstract class Portal[F[_], A](
        id:                PortalId,
    val preparedStatement: PreparedStatement[F, A]
  ) extends Managed[PortalId](id)

  sealed abstract class CommandPortal[F[_], A](
        id:              PortalId,
    val preparedCommand: PreparedCommand[F, A]
  ) extends Portal[F, A](id, preparedCommand) {
    def execute: F[Completion]
  }

  sealed abstract class QueryPortal[F[_], A, B](
        id:              PortalId,
    val preparedQuery:   PreparedQuery[F, A, B],
    val arguments:       A,
    val argumentsOrigin: Origin,
  ) extends Portal[F, A](id, preparedQuery) {
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

        def prepare[A](command: Command[A]): Resource[F, PreparedCommand[F, A]] =
          for {
            id <- atomic.parse(command)
            _  <- Resource.liftF(atomic.check(command, id))
          } yield new PreparedCommand[F, A](id, command) { pc =>
            def bind(args: A, origin: Origin): Resource[F, CommandPortal[F, A]] =
              atomic.bind(this, args, origin).map {
                new CommandPortal[F, A](_, pc) {
                  val execute: F[Completion] = atomic.execute(this)
                }
              }
          }

        def prepare[A, B](query: Query[A, B]): Resource[F, PreparedQuery[F, A, B]] =
          for {
            id <- atomic.parse(query)
            rd <- Resource.liftF(atomic.check(query, id))
          } yield new PreparedQuery[F, A, B](id, query, rd) { pq =>
            def bind(args: A, origin: Origin): Resource[F, QueryPortal[F, A, B]] =
              atomic.bind(this, args, origin).map {
                new QueryPortal[F, A, B](_, pq, args, origin) {
                  def execute(maxRows: Int): F[List[B] ~ Boolean] =
                    atomic.execute(this, maxRows)
                }
              }
          }

        def execute(command: Command[Void]): F[Completion] =
          atomic.execute(command)

        def execute[B](query: Query[Void, B]): F[List[B]] =
          atomic.execute(query)

        def startup(user: String, database: String): F[Unit] =
          atomic.startup(user, database)

        def transactionStatus: Signal[F, TransactionStatus] =
          bms.transactionStatus

      }

}


