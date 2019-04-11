// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.effect.{ Concurrent, ContextShift, Resource }
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.concurrent.Signal
import fs2.Stream
import skunk.{ Command, Query, Statement, ~, Void }
import skunk.data._
import skunk.net.message.RowDescription
import skunk.util.{ Namer, Origin }

/**
 * Interface for a Postgres database, expressed through high-level operations that rely on exchange
 * of multiple messages. Operations here can be executed concurrently and are non-cancelable.
 * Note that resource safety is not guaranteed here: statements and portals must be closed
 * explicitly.
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
   * Prepare a command (a statement that produces no rows), yielding a `Protocol.PreparedCommand` which
   * must be closed by the caller. Higher-level APIs may wish to encapsulate this in a `Resource`.
   */
  def prepareCommand[A](command: Command[A]): F[Protocol.PreparedCommand[F, A]]

  /**
   * Prepare a query (a statement that produces rows), yielding a `Protocol.PreparedCommand` which
   * must be closed by the caller. Higher-level APIs may wish to encapsulate this in a `Resource`.
   */
  def prepareQuery[A, B](query: Query[A, B]): F[Protocol.PreparedQuery[F, A, B]]

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
  // internals that aren't relevant to the end-user API.

  /**
   * A managed resource with an underlying database identifier and a `close` action which must be
   * called at some point, after which the resource is invalid. This is obviously bad so the high-
   * level API exposes this stuff with Resource.
   */
  trait Managed[F[_]] {
    def dbid: String
    def close: F[Unit]
  }

  trait PreparedStatement[F[_], A] extends Managed[F] {
    def statement: Statement[A]
  }

  abstract class PreparedCommand[F[_], A](
    val command: Command[A],
    val dbid: String,
  ) extends PreparedStatement[F, A] {
    def statement: Statement[A] = command
    def bind(args: A, argsOrigin: Origin): F[CommandPortal[F, A]]
  }

  abstract class PreparedQuery[F[_], A, B](
    val query: Query[A, B],
    val dbid: String,
    val rowDescription: RowDescription
  ) extends PreparedStatement[F, A] {
    def statement: Statement[A] = query
    def bind(args: A, argsOrigin: Origin): F[QueryPortal[F, A, B]]
  }

  trait Portal[F[_], A] extends Managed[F] {
    def preparedStatement: PreparedStatement[F, A]
  }

  abstract class CommandPortal[F[_], A](
    val dbid: String
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
   * Resource yielding a new `Protocol` with the given `host`, `port`, and statement checking policy.
   * @param host  Postgres server host
   * @param port  Postgres port, default 5432
   * @param check Check all `prepare` and `quick` statements for consistency with the schema. This
   *   is true by default and is recommended for development work.
   */
  def apply[F[_]: Concurrent: ContextShift](
    host:  String,
    port:  Int     = 5432
  ): Resource[F, Protocol[F]] =
    for {
      ams <- BufferedMessageSocket[F](host, port)
      ses <- Resource.liftF(Protocol.fromBufferedMessageSocket(ams))
    } yield ses

  /** Construct a `Protocol` by wrapping an `BufferedMessageSocket`. */
  private def fromBufferedMessageSocket[F[_]: Concurrent](
    ams:   BufferedMessageSocket[F]
  ): F[Protocol[F]] =
    for {
      nam <- Namer[F]
      sem <- Semaphore[F](1)
    } yield
      new Protocol[F] {

        val atomic: Atomic[F] =
          Atomic[F](ams, nam, sem)

        def notifications(maxQueued: Int): Stream[F, Notification] =
          ams.notifications(maxQueued)

        def parameters: Signal[F, Map[String, String]] =
          ams.parameters

        def prepareCommand[A](command: Command[A]): F[PreparedCommand[F, A]] =
          for {
            dbid <- atomic.parse(command)
            _    <- atomic.checkCommand(command, dbid).onError { case _ => atomic.closeStmt(dbid) }
            } yield new PreparedCommand[F, A](command, dbid) { pc =>
              def close: F[Unit] = atomic.closeStmt(dbid)
              def bind(args: A, origin: Origin): F[CommandPortal[F, A]] =
                atomic.bind(this, args, origin).map { dbid =>
                  new CommandPortal[F, A](dbid) {
                    def close: F[Unit] = atomic.closePortal(dbid)
                    def preparedCommand: PreparedCommand[F, A] = pc
                    def execute: F[Completion] = atomic.executeCommand(dbid)
                  }
                }
            }

        def prepareQuery[A, B](query: Query[A, B]): F[PreparedQuery[F, A, B]] =
          for {
            dbid           <- atomic.parse(query)
            rowDescription <- atomic.checkQuery(query, dbid).onError { case _ => atomic.closeStmt(dbid) }
          } yield new PreparedQuery[F, A, B](query, dbid, rowDescription) { pq =>
              def close: F[Unit] = atomic.closeStmt(dbid)
              def bind(args: A, origin: Origin): F[QueryPortal[F, A, B]] =
                atomic.bind(this, args, origin).map { portal =>
                  new QueryPortal[F, A, B] {
                    def dbid = portal
                    def preparedQuery = pq
                    def arguments = args
                    def argumentsOrigin = origin
                    def close: F[Unit] = atomic.closePortal(portal)
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
          ams.transactionStatus

      }

}


