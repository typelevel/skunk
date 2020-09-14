// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import skunk.net.message.BackendMessage
import skunk.util.Origin
import cats.effect.Concurrent
import cats.syntax.all._
import skunk.exception.ProtocolError
import skunk.net.BufferedMessageSocket.NetworkError

abstract class AbstractMessageSocket[F[_]: Concurrent]
  extends MessageSocket[F] {

    override def expect[B](f: PartialFunction[BackendMessage, B])(implicit or: Origin): F[B] =
      receive.flatMap { m =>
        if (f.isDefinedAt(m)) f(m).pure[F]
        else m match {
          case NetworkError(t) => Concurrent[F].raiseError(t)
          case m => Concurrent[F].raiseError(new ProtocolError(m, or))
        }
      }

  override def flatExpect[B](f: PartialFunction[BackendMessage, F[B]])(implicit or: Origin): F[B] =
      expect(f).flatten

  }