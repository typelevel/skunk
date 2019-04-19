// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.implicits._
import cats.MonadError
import skunk.~
import skunk.data.Completion
import skunk.exception.PostgresErrorException
import skunk.net.{ Protocol, MessageSocket }
import skunk.net.message.{ Execute => ExecuteMessage, _ }

trait Execute[F[_]] {
  def apply[A](portal: Protocol.CommandPortal[F, A]): F[Completion]
  def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean]
}

object Execute {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket]: Execute[F] =
    new Unroll[F] with Execute[F] {

      def apply[A](portal: Protocol.CommandPortal[F, A]): F[Completion] =
        exchange {
          for {
            _  <- send(ExecuteMessage(portal.id.value, 0))
            _  <- send(Flush)
            c  <- flatExpect {
              case CommandComplete(c)  => c.pure[F]
              case ErrorResponse(info) => syncAndFail[A](portal, info)
            }
          } yield c
        }

      def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean] =
        exchange {
          for {
            _  <- send(ExecuteMessage(portal.id.value, maxRows))
            _  <- send(Flush)
            rs <- unroll(portal)
          } yield rs
        }

      def syncAndFail[A](portal: Protocol.CommandPortal[F, A], info: Map[Char, String]): F[Completion] =
        for {
          hi <- history(Int.MaxValue)
          _  <- send(Sync)
          _  <- expect { case ReadyForQuery(_) => }
          a  <- new PostgresErrorException(
                  sql             = portal.preparedCommand.command.sql,
                  sqlOrigin       = Some(portal.preparedCommand.command.origin),
                  info            = info,
                  history         = hi,
                  arguments       = portal.preparedCommand.command.encoder.types.zip(portal.preparedCommand.command.encoder.encode(portal.arguments)),
                  argumentsOrigin = Some(portal.argumentsOrigin)
                ).raiseError[F, Completion]
        } yield a

    }

}