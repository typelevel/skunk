// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.effect._
import cats.implicits._
import fs2.concurrent.InspectableQueue
import scodec.codecs._
import skunk.net.message.{ Sync => _, _ }

/** A higher-level `BitVectorSocket` that speaks in terms of `Message`. */
trait MessageSocket[F[_]] {

  /**
   * Receive the next `BackendMessage`, or raise an exception if EOF is reached before a complete
   * message arrives.
   */
  def receive: F[BackendMessage]

  /** Send the specified message. */
  def send[A: FrontendMessage](a: A): F[Unit]

  /** Destructively read the last `n` messages from the circular buffer. */
  def history(max: Int): F[List[Either[Any, Any]]]

}

object MessageSocket {

  def fromBitVectorSocket[F[_]: Concurrent](bvs: BitVectorSocket[F]): F[MessageSocket[F]] =
    InspectableQueue.circularBuffer[F, Either[Any, Any]](10).map { cb =>
      new MessageSocket[F] {

        /**
        * Messages are prefixed with a 5-byte header consisting of a tag (byte) and a length (int32,
        * total including self but not including the tag) in network order.
        */
        val receiveImpl: F[BackendMessage] = {
          val header = byte ~ int32
          bvs.read(5).flatMap { bits =>
            val (tag, len) = header.decodeValue(bits).require
            val decoder    = BackendMessage.decoder(tag)
            bvs.read(len - 4).map(decoder.decodeValue(_).require)
          }
        }

        val receive: F[BackendMessage] =
          for {
            msg <- receiveImpl
            _   <- cb.enqueue1(Right(msg))
            // _   <- Sync[F].delay(println(s" ← $msg"))
          } yield msg

        def send[A](a: A)(implicit ev: FrontendMessage[A]): F[Unit] =
          for {
            // _ <- Sync[F].delay(println(s" → ${Console.BOLD}$a${Console.RESET}"))
            _ <- bvs.write(ev.fullEncoder.encode(a).require)
            _ <- cb.enqueue1(Left(a))
          } yield ()

        def history(max: Int) =
          cb.dequeueChunk1(max: Int).map(_.toList)

      }
    }

  def apply[F[_]: Concurrent](host: String, port: Int): Resource[F, MessageSocket[F]] =
    for {
      bvs <- BitVectorSocket(host, port)
      ms  <- Resource.liftF(fromBitVectorSocket(bvs))
    } yield ms


}
