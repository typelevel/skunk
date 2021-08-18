// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats._
import cats.effect._
import cats.syntax.all._
import fs2.Chunk
import scodec.bits.BitVector
import fs2.io.net.{Socket, SocketGroup, SocketOption}
import com.comcast.ip4s._
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
   * @group Constructors
   */
  def fromSocket[F[_]](
    socket:       Socket[F]
  )(
    implicit ev: MonadError[F, Throwable]
  ): BitVectorSocket[F] =
    new BitVectorSocket[F] {

      def readBytes(n: Int): F[Array[Byte]] =
        socket.readN(n).flatMap { c =>
          if (c.size == n) c.toArray.pure[F]
          else ev.raiseError(EofException(n, c.size))
        }

      override def read(nBytes: Int): F[BitVector] =
        readBytes(nBytes).map(BitVector(_))

      override def write(bits: BitVector): F[Unit] =
        socket.write(Chunk.array(bits.toByteArray))

    }

  /**
   * Construct a `BitVectorSocket` by constructing and wrapping a `Socket`.
   * @param host the remote hostname
   * @param port the remote port
   * @group Constructors
   */
  def apply[F[_]](
    host:         String,
    port:         Int,
    sg:           SocketGroup[F],
    sslOptions:   Option[SSLNegotiation.Options[F]],
  )(implicit ev: MonadError[F, Throwable]): Resource[F, BitVectorSocket[F]] =
    for {
      sock  <- sg.client(SocketAddress(Hostname.fromString(host).get, Port.fromInt(port).get), List(SocketOption.noDelay(true))) // TODO
      sockʹ <- sslOptions.fold(sock.pure[Resource[F, *]])(SSLNegotiation.negotiateSSL(sock, _))
    } yield fromSocket(sockʹ)

}

