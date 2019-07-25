// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package talk

import scala.concurrent.duration.FiniteDuration
import fs2.Chunk
import scodec.bits.BitVector
import cats.implicits._
import cats._
import cats.effect._
import skunk.net.BitVectorSocket
import scala.concurrent.duration._

// introduce scodec and bitvector

object BitVector1 {

  // So we have the socket interface which is *ok*

  trait Socket[F[_]] {
    def readN(numBytes: Int, timeout:         Option[FiniteDuration]): F[Option[Chunk[Byte]]]
    def write(bytes:    Chunk[Byte], timeout: Option[FiniteDuration]): F[Unit]
  }

  // But what we want is something more abstract. We'll assume that read and write timeouts are
  // uniform so they'll be part of construction rather than something we pass every time; and we're
  // going to treat EOF as an error because we always know how many bytes are coming because
  // messages are length-prefixed. If we it EOF it's an unrecoverable error.

  trait BitVectorSocket[F[_]] {
    def write(bits:  BitVector): F[Unit]
    def read(nBytes: Int):       F[BitVector]
  }

  // Ok so the question is, how do we get from here to there? We have a `Socket` and we want a
  // `BitVectorSocket`. Well the way we do this is with delegation, and it's a really really
  // common pattern in tagless style. You have an underlying low-level service and you wrap it up
  // with a higher-level service.

  object BitVectorSocket {

    // Introduce MonadError
    // say it's ok to match on Option in flatMap ... it's often really nice

    def fromSocket[F[_]: MonadError[?[_], Throwable]](
      socket:       Socket[F],
      readTimeout:  FiniteDuration,
      writeTimeout: FiniteDuration
    ): BitVectorSocket[F] =
      new BitVectorSocket[F] {

        def read(nBytes: Int): F[BitVector] =
          socket.readN(nBytes, Some(readTimeout)).flatMap {
            case Some(c) => BitVector(c.toArray).pure[F]
            case None =>
              new Exception(s"Fatal: EOF, expected $nBytes bytes.")
                .raiseError[F, BitVector]
          }

        def write(bits: BitVector): F[Unit] =
          socket.write(Chunk.array(bits.toByteArray), Some(writeTimeout))

      }

  }

}

object BitVector2 extends IOApp {
  import Socket3._
  import scodec.codecs.utf8

  def bitVectorSocket[F[_]: Concurrent: ContextShift](
    host:         String,
    port:         Int,
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration
  ): Resource[F, BitVectorSocket[F]] =
    socket(host, port).map { sock =>
      BitVectorSocket.fromSocket(
        sock,
        readTimeout,
        writeTimeout
      )
    }

  def runF[F[_]: Concurrent: ContextShift]: F[ExitCode] =
    bitVectorSocket[F](
      "google.com",
      80,
      1.second,
      1.seconds
    ).use { sock =>
      val req: BitVector = utf8.encode("GET / HTTP/1.0\n\n").require
      for {
        _ <- sock.write(req)
        bv <- sock.read(256)
        _ <- Sync[F].delay(println(utf8.decodeValue(bv).require))
      } yield ExitCode.Success
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO]

}
