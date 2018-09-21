package skunk
package proto

import cats._
import cats.implicits._
import skunk.proto.message._

object Startup {

  def apply[F[_]: Monad](sock: ActiveMessageSocket[F], msg: StartupMessage): F[Unit] =
    sock.send(msg) *> sock.expect {
      case AuthenticationOk =>
        sock.expect {
          case ReadyForQuery(_) => Monad[F].unit
        }
    }

}
