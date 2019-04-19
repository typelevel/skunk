// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

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