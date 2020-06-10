// Copyright (c) 2018-2020 by Rob Norris
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
import java.util.concurrent.ThreadFactory
import fs2.io.tcp.SocketGroup

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

  /** A default `AsynchronousChannelGroup` backed by a cached pool of daemon threads. */
  final val GlobalACG: AsynchronousChannelGroup =
    AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool {
      new ThreadFactory {
        var n = 1
        override def newThread(r: Runnable): Thread = {
          val t = new Thread(r, s"BitVectorSocket.GlobalACG-$n")
          t.setDaemon(true)
          n += 1
          t
        }
      }
    })

  /**
   * Construct a `BitVectorSocket` by wrapping an existing `Socket`.
   * @param socket the underlying `Socket`
   * @param readTimeout a read timeout, typically `Int.MaxValue.seconds` because we must wait
   *   actively for asynchronous messages.
   * @param writeTimeout a write timeout, typically no more than a few seconds.
   * @group Constructors
   */
  def fromSocket[F[_]](
    socket:       Socket[F],
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration
  )(
    implicit ev: MonadError[F, Throwable]
  ): BitVectorSocket[F] =
    new BitVectorSocket[F] {

      def readBytes(n: Int): F[Array[Byte]] =
        socket.readN(n, Some(readTimeout)).flatMap {
          case None => ev.raiseError(new Exception("Fatal: EOF"))
          case Some(c) =>
            if (c.size == n) c.toArray.pure[F]
            else ev.raiseError(new Exception(s"Fatal: EOF before $n bytes could be read.Bytes"))
        }

      override def read(nBytes: Int): F[BitVector] =
        readBytes(nBytes).map(BitVector(_))

      override def write(bits: BitVector): F[Unit] =
        socket.write(Chunk.array(bits.toByteArray), Some(writeTimeout))

    }

  /**
   * Construct a `BitVectorSocket` by constructing and wrapping a `Socket`.
   * @param host the remote hostname
   * @param port the remote port
   * @param readTimeout a read timeout, typically `Int.MaxValue.seconds` because we must wait
   *   actively for asynchronous messages.
   * @param writeTimeout a write timeout, typically no more than a few seconds.
   * @param acg an `AsynchronousChannelGroup` for completing asynchronous requests. There is
   *   typically one per application, and one is provided as `BitVectorSocket.GlobalACG`.
   * @group Constructors
   */
  def apply[F[_]: Concurrent: ContextShift](
    host:         String,
    port:         Int,
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration,
    sg:           SocketGroup,
  ): Resource[F, BitVectorSocket[F]] =
    sg.client[F](new InetSocketAddress(host, port)).map(fromSocket(_, readTimeout, writeTimeout))

}

