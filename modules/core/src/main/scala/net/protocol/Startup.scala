package skunk.net.protocol

import cats.FlatMap
import cats.implicits._
import skunk.net.MessageSocket
import skunk.net.message._

trait Startup[F[_]] {
  def apply(user: String, database: String): F[Unit]
}

object Startup {

  def apply[F[_]: FlatMap: Exchange: MessageSocket]: Startup[F] =
    new Startup[F] {
      def apply(user: String, database: String): F[Unit] =
        exchange {
          for {
            _ <- send(StartupMessage(user, database))
            _ <- expect { case AuthenticationOk => }
            _ <- expect { case ReadyForQuery(_) => }
          } yield ()
        }
    }

}