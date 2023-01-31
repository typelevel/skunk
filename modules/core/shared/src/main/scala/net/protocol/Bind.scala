// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.syntax.all._
import cats.MonadError
import skunk.exception.PostgresErrorException
import skunk.net.message.{ Bind => BindMessage, Close => _, _ }
import skunk.net.MessageSocket
import skunk.net.Protocol.{ PreparedStatement, PortalId }
import skunk.util.{ Origin, Namer }
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.AttributeKey
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer

trait Bind[F[_]] {

  def apply[A](
    statement:  PreparedStatement[F, A],
    args:       A,
    argsOrigin: Origin
  ): Resource[F, PortalId]

}

object Bind {

  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer](
    implicit ev: MonadError[F, Throwable]
  ): Bind[F] =
    new Bind[F] {

      override def apply[A](
        statement:  PreparedStatement[F, A],
        args:       A,
        argsOrigin: Origin
      ): Resource[F, PortalId] =
        Resource.make {
          exchange("bind") { (span: Span[F]) =>
            for {
              pn <- nextName("portal").map(PortalId(_))
              ea  = statement.statement.encoder.encode(args) // encoded args
              _  <- span.addAttributes(
                Attribute(AttributeKey.string("arguments"), ea.map(_.orNull).mkString(",")),
                Attribute(AttributeKey.string("portal-id"), pn.value)
              )
              _  <- send(BindMessage(pn.value, statement.id.value, ea))
              _  <- send(Flush)
              _  <- flatExpect {
                      case BindComplete        => ().pure[F]
                      case ErrorResponse(info) =>
                        for {
                          hi <- history(Int.MaxValue)
                          _  <- send(Sync)
                          _  <- expect { case ReadyForQuery(_) => }
                          a  <- PostgresErrorException.raiseError[F, Unit](
                                  sql             = statement.statement.sql,
                                  sqlOrigin       = Some(statement.statement.origin),
                                  info            = info,
                                  history         = hi,
                                  arguments       = statement.statement.encoder.types.zip(ea),
                                  argumentsOrigin = Some(argsOrigin)
                                )
                        } yield a
                    }
            } yield pn
          }
        } { Close[F].apply }

    }

}
