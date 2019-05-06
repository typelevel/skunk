package skunk.net

import skunk.net.message.BackendMessage
import skunk.util.Origin
import cats.effect.Concurrent
import cats.implicits._
import skunk.exception.ProtocolError

abstract class AbstractMessageSocket[F[_]: Concurrent]
  extends MessageSocket[F] {

    def expect[B](f: PartialFunction[BackendMessage, B])(implicit or: Origin): F[B] =
      receive.flatMap { m =>
        if (f.isDefinedAt(m)) f(m).pure[F]
        else Concurrent[F].raiseError(new ProtocolError(m, or))
      }

    def flatExpect[B](f: PartialFunction[BackendMessage, F[B]])(implicit or: Origin): F[B] =
      expect(f).flatten

  }