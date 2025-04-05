// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats._
import cats.effect.{Sync => _, _}
import cats.effect.implicits._
import cats.effect.std.{Console, Queue}
import cats.syntax.all._
import fs2.concurrent._
import fs2.Stream
import skunk.data._
import skunk.net.message._
import fs2.io.net.Socket
import scala.concurrent.duration.Duration

/**
 * A `MessageSocket` that buffers incoming messages, removing and handling asynchronous back-end
 * messages. This splits the protocol into a [logically] synchronous message exchange plus a set of
 * out-of-band broadcast channels that can be observed or ignored at the user's discretion.
 */
trait BufferedMessageSocket[F[_]] extends MessageSocket[F] {

  /**
   * `Signal` broadcasting the current `TransactionStatus` which is reported after each completed
   * message exchange. Note that this value may be stale in the case of a raised exception, which
   * should prompt the front end to send a `Sync` message but currently does not.
   */
  def transactionStatus: Signal[F, TransactionStatus]

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


  def backendKeyData: Deferred[F, BackendKeyData]

  /**
   * `Stream` of all channel notifications that this `Session` is subscribed to. Note that once
   * such a stream is started it is important to consume all notifications as quickly as possible to
   * avoid blocking message processing for other operations on the `Session` (although typically a
   * dedicated `Session` will receive channel notifications so this won't be an issue).
   * @param maxQueued the maximum number of notifications to hold in a queue before [semantically]
   *   blocking message exchange on the controlling `Session`.
   * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
   */
  def notifications(maxQueued: Int): Resource[F, Stream[F, Notification[String]]]


  // TODO: this is an implementation leakage, fold into the factory below
  protected def terminate: F[Unit]
}

object BufferedMessageSocket {

  def apply[F[_]: Temporal: Console](
    queueSize:    Int,
    debug:        Boolean,
    sockets: Resource[F, Socket[F]],
    sslOptions:   Option[SSLNegotiation.Options[F]],
    readTimeout:  Duration
  ): Resource[F, BufferedMessageSocket[F]] =
    for {
      ms  <- MessageSocket(debug, sockets, sslOptions, readTimeout)
      ams <- Resource.make(BufferedMessageSocket.fromMessageSocket[F](ms, queueSize))(_.terminate)
    } yield ams

  /**
   * Read one message and handle it if we can, otherwise emit it to the user. This is how we deal
   * with asynchronous messages, and messages that require us to record a bit of information that
   * the user might ask for later.
   */
  private def next[F[_]: MonadThrow](
    ms:    MessageSocket[F],
    xaSig: Ref[F, TransactionStatus],
    paSig: Ref[F, Map[String, String]],
    bkDef: Deferred[F, BackendKeyData],
    noTop: Topic[F, Notification[String]],
    queue: Queue[F, BackendMessage]
  ): F[Unit] = {
    def step: F[Unit] =  ms.receive.flatMap {
      // RowData is really the only hot spot so we special-case it to avoid the linear search. This
      // may be premature … need to benchmark and see if it matters.
      case m @ RowData(_)              => queue.offer(m)
      // This one is observed and then emitted.
      case m @ ReadyForQuery(s)        => xaSig.set(s) >> queue.offer(m) // observe and then emit
      // These are handled here and are never seen by the higher-level API.
      case     ParameterStatus(k, v)   => paSig.update(_ + (k -> v))
      case     NotificationResponse(n) => noTop.publish1(n).void // TODO -- what if it's closed?
      case     NoticeResponse(_)       => Monad[F].unit // TODO -- we're throwing these away!
      case m @ BackendKeyData(_, _)    => bkDef.complete(m).void
      // Everything else is passed through.
      case m                           => queue.offer(m)
    } >> step

    step.attempt.flatMap {
      case Left(e)  => queue.offer(NetworkError(e)) // publish the failure
      case Right(_) => Monad[F].unit
    }
  }

  // Here we read messages as they arrive, rather than waiting for the user to ask. This allows us
  // to handle asynchronous messages, which are dealt with here and not passed on. Other messages
  // are queued up and are typically consumed immediately, so a small queue size is probably fine.
  def fromMessageSocket[F[_]: Concurrent](
    ms:        MessageSocket[F],
    queueSize: Int
  ): F[BufferedMessageSocket[F]] =
    for {
      term  <- Ref[F].of[Option[Throwable]](None) // terminal error
      queue <- Queue.bounded[F, BackendMessage](queueSize)
      xaSig <- SignallingRef[F, TransactionStatus](TransactionStatus.Idle) // initial state (ok)
      paSig <- SignallingRef[F, Map[String, String]](Map.empty)
      bkSig <- Deferred[F, BackendKeyData]
      noTop <- Topic[F, Notification[String]]
      fib   <- next(ms, xaSig, paSig, bkSig, noTop, queue).start
    } yield
      new AbstractMessageSocket[F] with BufferedMessageSocket[F] {

        // n.b. there is a race condition here, prevented by the protocol semaphore
        override def receive: F[BackendMessage] =
          term.get.flatMap {
            case Some(t) => Concurrent[F].raiseError(t)
            case None    =>
              queue.take.flatMap {
                case e: NetworkError => term.set(Some(e.cause)) *> receive
                case m               => m.pure[F]
              }
          }

        override def send(message: FrontendMessage): F[Unit] =
          term.get.flatMap {
            case Some(t) => Concurrent[F].raiseError(t)
            case None    => ms.send(message)
          }

        override def transactionStatus: SignallingRef[F, TransactionStatus] = xaSig
        override def parameters: SignallingRef[F, Map[String, String]] = paSig
        override def backendKeyData: Deferred[F, BackendKeyData] = bkSig

        override def notifications(maxQueued: Int): Resource[F, Stream[F, Notification[String]]] =
          noTop.subscribeAwait(maxQueued)

        override protected def terminate: F[Unit] =
          fib.cancel *>                // stop processing incoming messages
          send(Terminate).attempt.void // server will close the socket when it sees this

        override def history(max: Int): F[List[Either[Any, Any]]] =
          ms.history(max)

      }

  // A poison pill that we place in the message queue to indicate that we're in a fatal error
  // condition, message processing has stopped, and any further attempts to send or receive should
  // result in `cause` being raised.
  private case class NetworkError(cause: Throwable) extends BackendMessage

}


