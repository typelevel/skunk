package skunk.net

import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.implicits._
import fs2.concurrent._
import fs2.Stream
import java.nio.channels.AsynchronousCloseException
import skunk.message._
import skunk.{ Identifier, SqlException }

// name??
trait ActiveMessageSocket[F[_]] {
  def receive: F[BackendMessage]
  def send[A: FrontendMessage](a: A): F[Unit]
  def transactionStatus: Signal[F, ReadyForQuery.Status]
  def parameters: Signal[F, Map[String, String]]
  def backendKeyData: Deferred[F, BackendKeyData]
  def notifications(maxQueued: Int): Stream[F, NotificationResponse]
  def expect[B](f: PartialFunction[BackendMessage, B]): F[B]
}

object ActiveMessageSocket {

  // A stream that reads one message and might emit it
  private def scatter[F[_]: Sync](
    ms:    MessageSocket[F],
    xaSig: Ref[F, ReadyForQuery.Status],
    paSig: Ref[F, Map[String, String]],
    bkDef: Deferred[F, BackendKeyData],
    noTop: Topic[F, NotificationResponse]
  ): Stream[F, BackendMessage] =
    Stream.eval(ms.receive).flatMap {

      // This message is passed on but we update the transaction status first.
      case m @ ReadyForQuery(s)       => Stream.eval(xaSig.set(s).as(m))

      // These messages are handled here and are never seen by higher-level code
      case     ParameterStatus(k, v)         => Stream.eval_(paSig.update(_ + (k -> v)))
      case n @ NotificationResponse(_, _, _) => Stream.eval_(noTop.publish1(n))
      case m @ BackendKeyData(_, _)          => Stream.eval_(bkDef.complete(m))
   // case     NoticeResponse(info) => publish these too, or maybe just log them? not a lot you can do with them

      // TODO: we should log and swallow Unknown messages but for now let them propagate because
      // it's easier to catch.

      // Anothing else gets passed on.
      case m => Stream.emit(m)

    }

  // Here we read messages as they arrive, rather than waiting for the user to ask. This allows us
  // to handle asynchronous messages, which are dealt with here and not passed on. Other messages
  // are queued up and are typically consumed immediately, so the queue need not be large … a queue
  // of size 1 is probably fine most of the time.
  def fromMessageSocket[F[_]: Concurrent](ms: MessageSocket[F], maxSize: Int): F[ActiveMessageSocket[F]] =
    for {
      queue <- Queue.bounded[F, BackendMessage](maxSize)
      xaSig <- SignallingRef[F, ReadyForQuery.Status](ReadyForQuery.Status.Idle)
      paSig <- SignallingRef[F, Map[String, String]](Map.empty)
      bkSig <- Deferred[F, BackendKeyData]
      noTop <- Topic[F, NotificationResponse](NotificationResponse(-1, Identifier.unsafeFromString("x"), "")) // lame, we filter this out on subscribe below
      _     <- scatter(ms, xaSig, paSig, bkSig, noTop).repeat.to(queue.enqueue).compile.drain.attempt.flatMap {
                 case Left(e: AsynchronousCloseException) => throw e // TODO: handle better … we  want to ignore this but may want to enqueue a Terminated message or something
                 case Left(e)  => Concurrent[F].delay(e.printStackTrace)
                 case Right(a) => a.pure[F]
               } .start
    } yield
      new ActiveMessageSocket[F] {

        def receive = queue.dequeue1
        def send[A: FrontendMessage](a: A) = ms.send(a)
        def transactionStatus = xaSig
        def parameters = paSig
        def backendKeyData = bkSig
        def notifications(maxQueued: Int) = noTop.subscribe(maxQueued).filter(_.pid > 0) // filter out the bogus initial value

        // Like flatMap but raises an error if a case isn't handled. This makes writing protocol
        // handlers much easier.
        def expect[B](f: PartialFunction[BackendMessage, B]): F[B] =
          receive.flatMap {
            case m if f.isDefinedAt(m) => f(m).pure[F]
            case ErrorResponse(map)    => Concurrent[F].raiseError(new SqlException(map))
            case m                     => Concurrent[F].raiseError(new RuntimeException(s"Unhandled: $m"))
          }

      }

    def apply[F[_]: ConcurrentEffect](host: String, port: Int): Resource[F, ActiveMessageSocket[F]] =
      MessageSocket(host, port).flatMap { ms =>
        val alloc = ActiveMessageSocket.fromMessageSocket[F](ms, 256)
        val free  = (_: ActiveMessageSocket[F]) => ms.send(Terminate)
        Resource.make(alloc)(free)
      }

}


