// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.implicits._
import cats.MonadError
import skunk.exception.PostgresErrorException
import skunk.net.message.{Bind => BindMessage, _}
import skunk.net.MessageSocket
import skunk.net.Protocol.{PortalId, PreparedStatement}
import skunk.util.{Namer, Origin}
import natchez.Trace

trait Bind[F[_]] {

  def apply[A](
    statement:  PreparedStatement[F, A],
    args:       A,
    argsOrigin: Origin
  ): Resource[F, PortalId]

}

object Bind {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Namer: Trace]: Bind[F] =
    new Bind[F] {

      override def apply[A](
        statement:  PreparedStatement[F, A],
        args:       A,
        argsOrigin: Origin
      ): Resource[F, PortalId] =
        Resource.make {
          exchange("bind") {
            for {
              pn <- nextName("portal").map(PortalId)
              _ <- Trace[F].put(
                    "arguments" -> args.toString,
                    "portal-id" -> pn.value
                  )
              _ <- send(BindMessage(pn.value, statement.id.value, statement.statement.encoder.encode(args)))
              _ <- send(Flush)
              _ <- flatExpect {
                    case BindComplete        => ().pure[F]
                    case ErrorResponse(info) => syncAndFail(statement, args, argsOrigin, info)
                  }
            } yield pn
          }
        } { Close[F].apply }

      def syncAndFail[A](
        statement:  PreparedStatement[F, A],
        args:       A,
        argsOrigin: Origin,
        info:       Map[Char, String]
      ): F[Unit] =
        for {
          hi <- history(Int.MaxValue)
          _ <- send(Sync)
          _ <- expect { case ReadyForQuery(_) => }
          a <- PostgresErrorException.raiseError[F, Unit](
                sql             = statement.statement.sql,
                sqlOrigin       = Some(statement.statement.origin),
                info            = info,
                history         = hi,
                arguments       = statement.statement.encoder.types.zip(statement.statement.encoder.encode(args)),
                argumentsOrigin = Some(argsOrigin)
              )
        } yield a

    }

}
