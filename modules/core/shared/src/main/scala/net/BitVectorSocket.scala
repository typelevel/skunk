// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.syntax.all._
import cats.effect._
import cats.effect.syntax.temporal._
import fs2.Chunk
import scodec.bits.BitVector
import fs2.io.net.Socket
import skunk.exception.EofException
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

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
  def fromSocket[F[_]](socket: Socket[F], readTimeout: Duration, carryRef: Ref[F, Chunk[Byte]])(implicit F: Temporal[F]): BitVectorSocket[F] =
    new BitVectorSocket[F] {
      private val withTimeout: F[Option[Chunk[Byte]]] => F[Option[Chunk[Byte]]] = readTimeout match {
        case _: Duration.Infinite   => identity
        case finite: FiniteDuration => _.timeout(finite)
      }

      override def read(nBytes: Int): F[BitVector] =
        // nb: unsafe for concurrent reads but protected by protocol mutex
        carryRef.get.flatMap(carry => readUntilN(nBytes, carry))

      private def readUntilN(nBytes: Int, carry: Chunk[Byte]): F[BitVector] =
        if (carry.size < nBytes) {
          withTimeout(socket.read(8192)).flatMap {
            case Some(bytes) => readUntilN(nBytes, carry ++ bytes)
            case None => F.raiseError(EofException(nBytes, carry.size.toInt))
          }
        } else {
          val (output, remainder) = carry.splitAt(nBytes)
          carryRef.set(remainder).as(output.toBitVector)
        }

      override def write(bits: BitVector): F[Unit] =
        socket.write(Chunk.byteVector(bits.bytes))
    }

  /**
   * Construct a `BitVectorSocket` by constructing and wrapping a `Socket`.
   * @param host the remote hostname
   * @param port the remote port
   * @group Constructors
   */
  def apply[F[_]: Temporal](
    sockets: Resource[F, Socket[F]],
    sslOptions:    Option[SSLNegotiation.Options[F]],
    readTimeout:   Duration
  ): Resource[F, BitVectorSocket[F]] =
    for {
      sock <- sockets
      sockʹ <- sslOptions.fold(sock.pure[Resource[F, *]])(SSLNegotiation.negotiateSSL(sock, _))
      carry <- Resource.eval(Ref.of(Chunk.empty[Byte]))
    } yield fromSocket(sockʹ, readTimeout, carry)

}

