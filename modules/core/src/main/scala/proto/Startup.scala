package skunk
package proto

import cats._
import cats.effect.concurrent._
import cats.implicits._
import skunk.proto.message._

object Startup {

  def apply[F[_]: FlatMap](
    sock: ActiveMessageSocket[F],
    sem:  Semaphore[F],
    msg:  StartupMessage
  ): F[Unit] =
    sem.withPermit {
      for {
        _ <- sock.send(msg)
        _ <- sock.expect { case AuthenticationOk => }
        _ <- sock.expect { case ReadyForQuery(_) => }
      } yield ()
    }

}
