// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats._
import cats.effect.{ Sync => _, _ }
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.syntax.all._
import fs2.concurrent._
import fs2.Stream
import skunk.data._
import skunk.net.message._
import scala.concurrent.duration._
import fs2.io.tcp.SocketGroup

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
  def notifications(maxQueued: Int): Stream[F, Notification[String]]


  // TODO: this is an implementation leakage, fold into the factory below
  protected def terminate: F[Unit]
}

object BufferedMessageSocket {

  def apply[F[_]: Concurrent: ContextShift](
    host:         String,
    port:         Int,
    queueSize:    Int,
    debug:        Boolean,
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration,
    sg:           SocketGroup,
    sslOptions:   Option[SSLNegotiation.Options[F]],
  ): Resource[F, BufferedMessageSocket[F]] =
    for {
      ms  <- MessageSocket(host, port, debug, readTimeout, writeTimeout, sg, sslOptions)
      ams <- Resource.make(BufferedMessageSocket.fromMessageSocket[F](ms, queueSize))(_.terminate)
    } yield ams

  /**
   * Read one message and handle it if we can, otherwise emit it to the user. This is how we deal
   * with asynchronous messages, and messages that require us to record a bit of information that
   * the user might ask for later.
   */
  private def next[F[_]: Functor](
    ms:    MessageSocket[F],
    xaSig: Ref[F, TransactionStatus],
    paSig: Ref[F, Map[String, String]],
    bkDef: Deferred[F, BackendKeyData],
    noTop: Topic[F, Notification[String]]
  ): Stream[F, BackendMessage] =
    Stream.eval(ms.receive).flatMap {

      // RowData is really the only hot spot so we special-case it to avoid the linear search. This
      // may be premature … need to benchmark and see if it matters.
    case m @ RowData(_)              => Stream.emit(m)

      // This one is observed and then emitted.
      case m @ ReadyForQuery(s)        => Stream.eval(xaSig.set(s).as(m)) // observe and then emit

      // These are handled here and are never seen by the higher-level API.
      case     ParameterStatus(k, v)   => Stream.eval_(paSig.update(_ + (k -> v)))
      case     NotificationResponse(n) => Stream.eval_(noTop.publish1(n))
      case     NoticeResponse(_)       => Stream.empty // TODO -- we're throwing these away!
      case m @ BackendKeyData(_, _)    => Stream.eval_(bkDef.complete(m))

      // Everything else is passed through.
      case m                           => Stream.emit(m)
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
      noTop <- Topic[F, Notification[String]](Notification(-1, Identifier.dummy, "")) // blech
      fib   <- next(ms, xaSig, paSig, bkSig, noTop).repeat.through(queue.enqueue).compile.drain.attempt.flatMap {
        case Left(e)  => queue.enqueue1(NetworkError(e)) // publish the failure
        case Right(a) => a.pure[F]
      } .start
    } yield
      new AbstractMessageSocket[F] with BufferedMessageSocket[F] {

        // n.b. there is a race condition here, prevented by the protocol semaphore
        override def receive: F[BackendMessage] =
          term.get.flatMap {
            case Some(t) => Concurrent[F].raiseError(t)
            case None    =>
              queue.dequeue1.flatMap {
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

        override def notifications(maxQueued: Int): Stream[F, Notification[String]] =
          noTop.subscribe(maxQueued).filter(_.pid > 0) // filter out the bogus initial value

        override protected def terminate: F[Unit] =
          fib.cancel *>      // stop processing incoming messages
          send(Terminate) // server will close the socket when it sees this

        override def history(max: Int): F[List[Either[Any, Any]]] =
          ms.history(max)

      }

  // A poison pill that we place in the message queue to indicate that we're in a fatal error
  // condition, message processing has stopped, and any further attempts to send or receive should
  // result in `cause` being raised.
  private case class NetworkError(cause: Throwable) extends BackendMessage

}


