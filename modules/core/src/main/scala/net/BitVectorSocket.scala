// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats._
import cats.effect._
import cats.implicits._
import fs2.Chunk
import fs2.io.tcp.Socket
import scala.concurrent.duration.FiniteDuration
import scodec.bits.BitVector
import java.net.InetSocketAddress
import java.nio.channels._
import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import skunk.syntax.resource._

/** A higher-level `Socket` interface defined in terms of `BitVector`. */
trait BitVectorSocket[F[_]] {

  /** Write the specified bits to the socket. */
  def write(bits: BitVector): F[Unit]

  /**
   * Read `nBytes` bytes (not bits!) from the socket, or fail with an exception if EOF is reached
   * before `nBytes` bytes are received.
   */
  def read(nBytes: Int): F[BitVector]
}

object BitVectorSocket {

  // N.B. we need to fix `socket` to `IO` otherwise we end up with a `ConcurrentEffect` constraint
  // which is too strong (it prevents us from implementing `ApplicativeAsk` for instance). This is
  // a design compromise that we may be able to fix at some point.

  def fromSocket[F[_]: LiftIO](
    socket:       Socket[IO],
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration
  )(
    implicit ev: MonadError[F, Throwable]
  ): BitVectorSocket[F] =
    new BitVectorSocket[F] {

      def readBytes(n: Int): F[Array[Byte]] =
        socket.readN(n, Some(readTimeout)).to[F].flatMap {
          case None => ev.raiseError(new Exception("Fatal: EOF"))
          case Some(c) =>
            if (c.size == n) c.toArray.pure[F]
            else ev.raiseError(new Exception(s"Fatal: EOF before $n bytes could be read.Bytes"))
        }

      def read(nBytes: Int): F[BitVector] =
        readBytes(nBytes).map(BitVector(_))

      def write(bits: BitVector): F[Unit] =
        socket.write(Chunk.array(bits.toByteArray), Some(writeTimeout)).to[F]

    }

  def apply[F[_]: LiftIO: MonadError[?[_], Throwable]](host: String, port: Int): Resource[F, BitVectorSocket[F]] = {

    implicit val ioContextShift: ContextShift[IO] =
      IO.contextShift(ExecutionContext.global)

    val acg: Resource[IO, AsynchronousChannelGroup] = {
      val alloc = IO.delay(AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool()))
      val free  = (acg: AsynchronousChannelGroup) => IO.delay(acg.shutdown())
      Resource.make(alloc)(free)
    }

    val sock: Resource[IO, Socket[IO]] =
      acg.flatMap { implicit acg =>
        fs2.io.tcp.client[IO](new InetSocketAddress(host, port))
      }

    sock.map(fromSocket(_, 1.day, 5.seconds)).mapK(λ[IO ~> F](_.to))

  }

}

