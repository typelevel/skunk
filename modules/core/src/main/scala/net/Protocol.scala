// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.effect.{ Concurrent, ContextShift, Resource }
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.concurrent.Signal
import fs2.Stream
import skunk.{ Command, Query, ~, Void }
import skunk.data._
import skunk.util.{ CallSite, Namer, Origin }

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
  def prepareQuery[A, B](query: Query[A, B], callSite: Option[CallSite]): F[Protocol.PreparedQuery[F, A, B]]

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

  trait CommandPortal[F[_]] {
    def close: F[Unit]
    def execute: F[Completion]
  }

  trait PreparedCommand[F[_], A] {
    def command: Command[A]
    def bind(args: A, argsOrigin: Origin): F[Protocol.CommandPortal[F]]
    def check: F[Unit]
    def close: F[Unit]
  }

  trait PreparedQuery[F[_], A, B] {
    def query: Query[A, B]
    def close: F[Unit]
    def check: F[Unit]
    def bind(args: A, argsOrigin: Origin): F[Protocol.QueryPortal[F, B]]
  }

  trait QueryPortal[F[_], A] {
    def close: F[Unit]
    def execute(maxRows: Int): F[List[A] ~ Boolean]
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
    port:  Int     = 5432,
    check: Boolean = true
  ): Resource[F, Protocol[F]] =
    for {
      ams <- BufferedMessageSocket[F](host, port)
      ses <- Resource.liftF(Protocol.fromBufferedMessageSocket(ams, check))
    } yield ses

  /** Construct a `Protocol` by wrapping an `BufferedMessageSocket`. */
  private def fromBufferedMessageSocket[F[_]: Concurrent](
    ams:   BufferedMessageSocket[F],
    check: Boolean
  ): F[Protocol[F]] =
    for {
      nam <- Namer[F]
      sem <- Semaphore[F](1)
    } yield new ProtocolImpl(ams, nam, sem, check)

  /**
   * `Protocol` implementation.
   * @param ams `BufferedMessageSocket` that manages message exchange.
   * @param nam `Namer` for giving unique (per session) names for prepared statements and portals.
   * @param sem Single-key `Semaphore` used as a mutex for message exchanges. Every "conversation"
   *   must be conducted while holding the mutex because we may have interleaved streams.
   * @param check Check all `prepare` and `quick` statements for consistency with the schema.
   */
  private final class ProtocolImpl[F[_]: Concurrent](
    ams:   BufferedMessageSocket[F],
    nam:   Namer[F],
    sem:   Semaphore[F],
    check: Boolean
  ) extends Protocol[F] {

    val atomic: Atomic[F] =
      new Atomic[F](ams, nam, sem)

    def notifications(maxQueued: Int): Stream[F, Notification] =
      ams.notifications(maxQueued)

    def parameters: Signal[F, Map[String, String]] =
      ams.parameters

    def prepareCommand[A](cmd: Command[A]): F[Protocol.PreparedCommand[F, A]] =
      atomic.parse(cmd.sql, None, cmd.encoder).map { stmt =>
        new Protocol.PreparedCommand[F, A] {
          def command: Command[A] = cmd
          def check: F[Unit] = atomic.checkCommand(cmd, stmt)
          def close: F[Unit] = atomic.closeStmt(stmt)
          def bind(args: A, origin: Origin): F[Protocol.CommandPortal[F]] =
            atomic.bind(cmd.sql, None, stmt, cmd.encoder, args, origin).map { portalName =>
              new Protocol.CommandPortal[F] {
                def close: F[Unit] = atomic.closePortal(portalName)
                def execute: F[Completion] = atomic.executeCommand(portalName)
              }
            }
        }
      } .flatMap { ps => ps.check.whenA(check).as(ps) }

    def prepareQuery[A, B](query0: Query[A, B], callSite: Option[CallSite]): F[Protocol.PreparedQuery[F, A, B]] =
      atomic.parse(query0.sql, query0.origin, query0.encoder).map { stmt =>
        new Protocol.PreparedQuery[F, A, B] {
          def query = query0
          def check: F[Unit] = atomic.checkQuery(query0, stmt)
          def close: F[Unit] = atomic.closeStmt(stmt)
          def bind(args: A, origin: Origin): F[Protocol.QueryPortal[F, B]] =
            atomic.bind(query0.sql, query0.origin, stmt, query0.encoder, args, origin).map { portal =>
              new Protocol.QueryPortal[F, B] {
                def close: F[Unit] = atomic.closePortal(portal)
                def execute(maxRows: Int): F[List[B] ~ Boolean] =
                  atomic.executeQuery(query0, portal, maxRows)
              }
            }
        }
      } .flatMap { ps => ps.check.whenA(check).as(ps) }

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


