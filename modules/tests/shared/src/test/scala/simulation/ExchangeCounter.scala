// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.effect._
import fs2.Stream
import fs2.concurrent.{ Signal, SignallingRef }
import skunk.data.{ Notification, TransactionStatus }
import skunk.net.{ AbstractMessageSocket, BufferedMessageSocket, MessageSocket }
import skunk.net.message.{ BackendKeyData, BackendMessage, FrontendMessage, ParameterStatus, ReadyForQuery }

/** What one operation cost on the wire.
  *
  * `exchanges` counts `send`→`receive` transitions -- the points where the client stopped writing and
  * blocked on a reply. `writes` and `bytesSent` move independently of it, since nothing buffers
  * frontend messages, so three pipelined messages still cost three writes.
  */
final case class ExchangeCounts(exchanges: Int, writes: Int, reads: Int, bytesSent: Long)

/** A `MessageSocket` decorator that counts what passes through it.
  *
  * It implements `BufferedMessageSocket` so it can be passed to `Protocol.fromMessageSocket`, but
  * wraps whichever `MessageSocket` it is given. Against a live server that must be the real
  * `BufferedMessageSocket`: that reads its inner socket from a background fiber, so below it receives
  * are eager and transitions measure nothing.
  *
  * The count is of the protocol code's own `send`/`receive` interleaving, not of observed blocking.
  * The client always sends `Flush` or `Sync` before reading, so every transition is a real round trip.
  */
final class ExchangeCounter private (
  underlying: MessageSocket[IO],
  state:      Ref[IO, ExchangeCounter.State],
  xaSig:      SignallingRef[IO, TransactionStatus],
  paSig:      SignallingRef[IO, Map[String, String]]
) extends AbstractMessageSocket[IO] with BufferedMessageSocket[IO] {

  override def receive: IO[BackendMessage] =
    state.update(_.reading) *> underlying.receive.flatTap {
      // Observed, not filtered: this is a pass-through decorator. Tracking them lets a scenario
      // use Session.transaction.
      case ReadyForQuery(s)      => xaSig.set(s)
      case ParameterStatus(k, v) => paSig.update(_ + (k -> v))
      case _                     => IO.unit
    }

  override def send(message: FrontendMessage): IO[Unit] =
    state.update(_.writing(message.encode.size / 8)) *> underlying.send(message)

  override def history(max: Int): IO[List[Either[Any, Any]]] =
    underlying.history(max)

  /** Counts since construction, or since the last `reset`. */
  val counts: IO[ExchangeCounts] =
    state.get.map(_.counts)

  /** Zero the counters, forgetting whether the last operation was a write, so the next read does not
    * score an exchange against anything before the reset.
    */
  val reset: IO[Unit] =
    state.set(ExchangeCounter.State.empty)

  override def transactionStatus: Signal[IO, TransactionStatus] = xaSig
  override def parameters: Signal[IO, Map[String, String]] = paSig

  // Correct for a socket that is never sent a NotificationResponse.
  override def notifications(maxQueued: Int): Resource[IO, Stream[IO, Notification[String]]] =
    Resource.pure(Stream.empty)

  // A Deferred that is never completed would hang a caller, so fail with a reason instead.
  override def backendKeyData: Deferred[IO, BackendKeyData] =
    sys.error("ExchangeCounter does not model BackendKeyData: no simulated backend sends it")

  // The wrapped socket's Resource finalizer does the real work.
  override def terminate: IO[Unit] = IO.unit

  override def isHealthy: IO[Boolean] = IO.pure(true)
}

object ExchangeCounter {

  final case class State(
    exchanges:   Int,
    writes:      Int,
    reads:       Int,
    bytesSent:   Long,
    lastWasSend: Boolean
  ) {

    def writing(bytes: Long): State =
      copy(writes = writes + 1, bytesSent = bytesSent + bytes, lastWasSend = true)

    def reading: State =
      if (lastWasSend) copy(exchanges = exchanges + 1, reads = reads + 1, lastWasSend = false)
      else            copy(reads = reads + 1)

    def counts: ExchangeCounts =
      ExchangeCounts(exchanges, writes, reads, bytesSent)

  }

  object State {
    val empty: State = State(0, 0, 0, 0L, false)
  }

  def apply(underlying: MessageSocket[IO]): IO[ExchangeCounter] =
    for {
      st <- Ref[IO].of(State.empty)
      xa <- SignallingRef[IO, TransactionStatus](TransactionStatus.Idle)
      pa <- SignallingRef[IO, Map[String, String]](Map.empty)
    } yield new ExchangeCounter(underlying, st, xa, pa)

}
