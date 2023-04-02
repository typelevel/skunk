// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.Applicative
import cats.effect._
import cats.effect.std.Console
import cats.effect.std.Queue
import cats.syntax.all._
import scala.io.AnsiColor
import scodec.codecs._
import skunk.net.message.{ Sync => _, _ }
import skunk.util.Origin
import fs2.io.net.{ SocketGroup, SocketOption }
import scala.concurrent.duration.Duration

/** A higher-level `BitVectorSocket` that speaks in terms of `Message`. */
trait MessageSocket[F[_]] {

  /**
   * Receive the next `BackendMessage`, or raise an exception if EOF is reached before a complete
   * message arrives.
   */
  def receive: F[BackendMessage]

  /** Send the specified message. */
  def send(message: FrontendMessage): F[Unit]

  /** Destructively read the last `n` messages from the circular buffer. */
  def history(max: Int): F[List[Either[Any, Any]]]

  def expect[B](f: PartialFunction[BackendMessage, B])(implicit or: Origin): F[B]
  def flatExpect[B](f: PartialFunction[BackendMessage, F[B]])(implicit or: Origin): F[B]

}

object MessageSocket {

  def fromBitVectorSocket[F[_]: Concurrent: Console](
    bvs: BitVectorSocket[F],
    debugEnabled: Boolean
  ): F[MessageSocket[F]] =
    Queue.circularBuffer[F, Either[Any, Any]](10).map { cb =>
      new AbstractMessageSocket[F] with MessageSocket[F] {

        private def debug(msg: => String): F[Unit] =
          if (debugEnabled) Console[F].println(msg) else Concurrent[F].unit

        /**
        * Messages are prefixed with a 5-byte header consisting of a tag (byte) and a length (int32,
        * total including self but not including the tag) in network order.
        */
        val receiveImpl: F[BackendMessage] = {
          bvs.read(5).flatMap { bits =>
            val tag = byte.decodeValue(bits).require
            val len = bits.drop(8).toInt()
            val decoder    = BackendMessage.decoder(tag)
            bvs.read(len - 4).map(decoder.decodeValue(_).require)
          } .onError {
            case t => debug(s" ← ${AnsiColor.RED}${t.getMessage}${AnsiColor.RESET}")
          }
        }

        override val receive: F[BackendMessage] =
          for {
            msg <- receiveImpl
            _   <- cb.offer(Right(msg))
            _   <- debug(s" ← ${AnsiColor.GREEN}$msg${AnsiColor.RESET}")
          } yield msg

        override def send(message: FrontendMessage): F[Unit] =
          for {
            _ <- debug(s" → ${AnsiColor.YELLOW}$message${AnsiColor.RESET}")
            _ <- bvs.write(message.encode)
            _ <- cb.offer(Left(message))
          } yield ()

        override def history(max: Int): F[List[Either[Any, Any]]] =
          cb.take.flatMap { first =>
            def pump(acc: List[Either[Any, Any]]): F[List[Either[Any, Any]]] =
              cb.tryTake.flatMap {
                case Some(e) => pump(e :: acc)
                case None => Applicative[F].pure(acc.reverse)
              }
            pump(List(first))
          }
      }
    }

  def apply[F[_]: Console: Temporal](
    host:         String,
    port:         Int,
    debug:        Boolean,
    sg:           SocketGroup[F],
    socketOptions: List[SocketOption],
    sslOptions:   Option[SSLNegotiation.Options[F]],
    readTimeout:  Duration
  ): Resource[F, MessageSocket[F]] =
    for {
      bvs <- BitVectorSocket(host, port, sg, socketOptions, sslOptions, readTimeout)
      ms  <- Resource.eval(fromBitVectorSocket(bvs, debug))
    } yield ms


}
