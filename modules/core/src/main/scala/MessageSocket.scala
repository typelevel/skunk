package skunk

import cats.effect._
import cats.implicits._
import scodec.codecs._
import scodec.bits._
import skunk.proto.message._

/** A higher-level socket interface defined in terms of `Message`. */
trait MessageSocket[F[_]] {
  def receive: F[BackendMessage]
  def send[A: FrontendMessage](a: A): F[Unit]
}

object MessageSocket {

  def fromBitVectorSocket[F[_]: Sync](bvs: BitVectorSocket[F]): MessageSocket[F] =
    new MessageSocket[F] {

      /**
       * Messages are prefixed with a 5-byte header consisting of a tag (byte) and a length (int32,
       * total including self but not including the tag) in network order. We are in charge of
       * stripping that stuff off and returning the tag and payload for further decoding.
       */
      val rawReceive: F[(Byte, BitVector)] = {
        val header = byte ~ int32
        bvs.readBitVector(5).flatMap { bits =>
          val (tag, len) = header.decodeValue(bits).require
          bvs.readBitVector(len - 4).map((tag, _))
        }
      }

      def receive: F[BackendMessage] =
        for {
          msg <- rawReceive.map { case (tag, data) => BackendMessage.decoder(tag).decodeValue(data).require } // TODO: handle decoding error
          _   <- Sync[F].delay(println(s"${Console.GREEN}$msg${Console.RESET}"))
        } yield msg

      def send[A](a: A)(
        implicit ev: FrontendMessage[A]
      ): F[Unit] =
        for {
          _ <- Sync[F].delay(println(s"${Console.YELLOW}$a${Console.RESET}"))
          _ <- bvs.write(ev.fullEncoder.encode(a).require)
        } yield ()

    }

}
