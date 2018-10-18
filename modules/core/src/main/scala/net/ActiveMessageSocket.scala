package skunk.net

import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.implicits._
import fs2.concurrent._
import fs2.Stream
import skunk.data._
import skunk.net.message._

// name??
trait ActiveMessageSocket[F[_]] {
  def receive: F[BackendMessage]
  def send[A: FrontendMessage](a: A): F[Unit]
  def transactionStatus: Signal[F, TransactionStatus]
  def parameters: Signal[F, Map[String, String]]
  def backendKeyData: Deferred[F, BackendKeyData]
  def notifications(maxQueued: Int): Stream[F, Notification]
  def expect[B](f: PartialFunction[BackendMessage, B]): F[B]

  // TODO: this is an implementation leakage, fold into the factory below
  protected def terminate: F[Unit]
}

object ActiveMessageSocket {

  def apply[F[_]: ConcurrentEffect](
    host:      String,
    port:      Int,
    queueSize: Int = 256
  ): Resource[F, ActiveMessageSocket[F]] =
    for {
      ms  <- MessageSocket(host, port)
      ams <- Resource.make(ActiveMessageSocket.fromMessageSocket[F](ms, queueSize))(_.terminate)
    } yield ams

  /**
   * Read one message and handle it if we can, otherwise emit it to the user. This is how we deal
   * with asynchronous messages, and messages that require us to record a bit of information that
   * the user might ask for later.
   */
  private def next[F[_]: Sync](
    ms:    MessageSocket[F],
    xaSig: Ref[F, TransactionStatus],
    paSig: Ref[F, Map[String, String]],
    bkDef: Deferred[F, BackendKeyData],
    noTop: Topic[F, Notification]
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
      case m @ BackendKeyData(_, _)    => Stream.eval_(bkDef.complete(m))

      // Lift this to an exception .. sensible?
      case      ErrorResponse(map)     => Stream.raiseError[F](new SqlException(map))

      // Everything else is passed through.
      case m                           => Stream.emit(m)
    }

  // Here we read messages as they arrive, rather than waiting for the user to ask. This allows us
  // to handle asynchronous messages, which are dealt with here and not passed on. Other messages
  // are queued up and are typically consumed immediately, so a small queue size is probably fine.
  private def fromMessageSocket[F[_]: Concurrent](
    ms:       MessageSocket[F],
    queueSize: Int
  ): F[ActiveMessageSocket[F]] =
    for {
      queue <- Queue.bounded[F, BackendMessage](queueSize)
      xaSig <- SignallingRef[F, TransactionStatus](TransactionStatus.Idle) // initial state (ok)
      paSig <- SignallingRef[F, Map[String, String]](Map.empty)
      bkSig <- Deferred[F, BackendKeyData]
      noTop <- Topic[F, Notification](Notification(-1, Identifier.unsafeFromString("x"), "")) // blech
      fib   <- next(ms, xaSig, paSig, bkSig, noTop).repeat.to(queue.enqueue).compile.drain.start
    } yield
      new ActiveMessageSocket[F] {

        def receive = queue.dequeue1
        def send[A: FrontendMessage](a: A) = ms.send(a)
        def transactionStatus = xaSig
        def parameters = paSig
        def backendKeyData = bkSig

        def notifications(maxQueued: Int) =
          noTop.subscribe(maxQueued).filter(_.pid > 0) // filter out the bogus initial value

        protected def terminate: F[Unit] =
          fib.cancel *>      // stop processing incoming messages
          ms.send(Terminate) // server will close the socket when it sees this

        // Like flatMap but raises an error if a case isn't handled. This makes writing protocol
        // handlers much easier.
        def expect[B](f: PartialFunction[BackendMessage, B]): F[B] =
          receive.flatMap {
            case m if f.isDefinedAt(m) => f(m).pure[F]
            case m                     => Concurrent[F].raiseError(new RuntimeException(s"Unhandled: $m"))
          }

      }


}


