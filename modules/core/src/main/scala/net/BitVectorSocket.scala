// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats._
import cats.effect._
import cats.syntax.all._
import fs2.Chunk
import fs2.io.tcp.Socket
import scala.concurrent.duration.FiniteDuration
import scodec.bits.BitVector
import java.net.InetSocketAddress
import fs2.io.tcp.SocketGroup
import skunk.exception.EofException

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
          case None => ev.raiseError(EofException(n, 0))
          case Some(c) =>
            if (c.size == n) c.toArray.pure[F]
            else ev.raiseError(EofException(n, c.size))
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
   * @group Constructors
   */
  def apply[F[_]: Concurrent: ContextShift](
    host:         String,
    port:         Int,
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration,
    sg:           SocketGroup,
    sslOptions:   Option[SSLNegotiation.Options[F]],
  ): Resource[F, BitVectorSocket[F]] =
    for {
      sock  <- sg.client[F](new InetSocketAddress(host, port))
      sockʹ <- sslOptions.fold(sock.pure[Resource[F, *]])(SSLNegotiation.negotiateSSL(sock, readTimeout, writeTimeout, _))
    } yield fromSocket(sockʹ, readTimeout, writeTimeout)

}

