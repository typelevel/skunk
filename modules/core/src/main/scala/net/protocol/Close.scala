package skunk.net.protocol

import cats.FlatMap
import cats.implicits._
import skunk.net.message.{ Close => CloseMessage, Flush, CloseComplete }
import skunk.net.MessageSocket

trait Close[F[_]] {
  def apply(message: CloseMessage): F[Unit]
}

object Close {

  def apply[F[_]: FlatMap: Exchange: MessageSocket]: Close[F] =
    new Close[F] {
      def apply(message: CloseMessage): F[Unit] =
        exchange {
          for {
            _ <- send(message)
            _ <- send(Flush)
            _ <- expect { case CloseComplete => }
          } yield ()
        }
    }

}