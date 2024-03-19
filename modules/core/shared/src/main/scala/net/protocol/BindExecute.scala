// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.syntax.all._
import cats.MonadError
import skunk.exception._
import skunk.net.message.{ Bind => BindMessage, Close => _, _ }
import skunk.net.MessageSocket
import skunk.net.Protocol.PortalId
import skunk.util.{ Origin, Namer }
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{Span, Tracer}
import skunk.RedactionStrategy
import skunk.net.Protocol
import skunk.data.Completion
import skunk.net.protocol.exchange

trait BindExecute[F[_]] {

  def command[A](
    statement:  Protocol.PreparedCommand[F, A],
    args:       A,
    argsOrigin: Origin,
    redactionStrategy: RedactionStrategy
  ): Resource[F, Protocol.CommandPortal[F, A]]

}

object BindExecute {
  
  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer](
    implicit ev: MonadError[F, Throwable]
  ): BindExecute[F] =
    new BindExecute[F] {

      def command[A](
        statement:  Protocol.PreparedCommand[F, A],
        args:       A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy
      ): Resource[F, Protocol.CommandPortal[F, A]] = {
        val ea  = statement.statement.encoder.encode(args) // encoded args
        
        def preBind(span: Span[F]): F[PortalId] = for {
              pn <- nextName("portal").map(PortalId(_))
              _  <- span.addAttributes(
                Attribute("arguments", redactionStrategy.redactArguments(ea).map(_.orNull).mkString(",")),
                Attribute("portal-id", pn.value)
              )
              _  <- send(BindMessage(pn.value, statement.id.value, ea.map(_.map(_.value))))
        } yield pn

        val postBind: F[Unit] = flatExpect {
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

        def preExec(portal: PortalId):F[Unit] = send(Execute(portal.value, 0))

        val postExec: F[Completion] = flatExpect {
          case CommandComplete(c) => send(Sync) *> expect { case ReadyForQuery(_) => c } // https://github.com/tpolecat/skunk/issues/210

          case EmptyQueryResponse =>
            send(Sync) *>
            expect { case ReadyForQuery(_) => } *>
            new EmptyStatementException(statement.command).raiseError[F, Completion]

          case CopyOutResponse(_) =>
            receive.iterateUntil {
              case CommandComplete(_) => true
              case _                  => false
            } *>
            new CopyNotSupportedException(statement.command).raiseError[F, Completion]

          case CopyInResponse(_) =>
            send(CopyFail) *>
            expect { case ErrorResponse(_) => } *>
            send(Sync) *>
            expect { case ReadyForQuery(_) => } *>
            new CopyNotSupportedException(statement.command).raiseError[F, Completion]

          case ErrorResponse(info) =>
            for {
              hi <- history(Int.MaxValue)
              _  <- send(Sync)
              _  <- expect { case ReadyForQuery(_) => }
              redactedArgs = statement.command.encoder.types.zip(
                redactionStrategy.redactArguments(statement.command.encoder.encode(args)))
              a  <- new PostgresErrorException(
                      sql             = statement.command.sql,
                      sqlOrigin       = Some(statement.command.origin),
                      info            = info,
                      history         = hi,
                      arguments       = redactedArgs,
                      argumentsOrigin = Some(argsOrigin)
                    ).raiseError[F, Completion]
            } yield a
        }

        Resource.make {
          exchange("bind+execute"){ (span: Span[F]) =>
            for {
              pn <- preBind(span)
              _  <- preExec(pn) 
              _  <- send(Flush)
              _  <- postBind
              c  <- postExec
            } yield new Protocol.CommandPortal[F, A](pn, statement, args, argsOrigin) {
              def execute: F[Completion] = c.pure
            }
          }
        } { portal => Close[F].apply(portal.id)}

      }
    }


}
