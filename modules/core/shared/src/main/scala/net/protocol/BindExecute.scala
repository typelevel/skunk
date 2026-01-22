// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.syntax.all._
import cats.effect.Concurrent
import skunk.~
import skunk.exception._
import skunk.net.message.{ Bind => BindMessage, Execute => ExecuteMessage, Close => _, _ }
import skunk.net.MessageSocket
import skunk.net.Protocol.PortalId
import skunk.util.{ Origin, Namer }
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{Span, Tracer}
import skunk.RedactionStrategy
import skunk.net.Protocol
import skunk.data.Completion
import skunk.net.protocol.exchange
import cats.effect.kernel.Deferred
import org.typelevel.otel4s.metrics.Histogram

trait BindExecute[F[_]] {

  def command[A](
    statement:  Protocol.PreparedCommand[F, A],
    args:       A,
    argsOrigin: Origin,
    redactionStrategy: RedactionStrategy
  ): Resource[F, Protocol.CommandPortal[F, A]]

  def query[A, B](
    statement:  Protocol.PreparedQuery[F, A, B],
    args:       A,
    argsOrigin: Origin,
    redactionStrategy: RedactionStrategy,
    initialSize: Int
  ): Resource[F, Protocol.QueryPortal[F, A, B]]
}

object BindExecute {
  
  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer](opDuration: Histogram[F, Double])(
    implicit ev: Concurrent[F]
  ): BindExecute[F] =
    new Unroll[F] with BindExecute[F] {
      
      def bindExchange[A](
        statement: Protocol.PreparedStatement[F, A],
        args: A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy
      ):(Span[F] => F[PortalId], F[Unit]) = {
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
        (preBind, postBind)
      }

      def command[A](
        statement:  Protocol.PreparedCommand[F, A],
        args:       A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy
      ): Resource[F, Protocol.CommandPortal[F, A]] = {

        val (preBind, postBind) = bindExchange(statement, args, argsOrigin, redactionStrategy)

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
          exchange("bind+execute", opDuration){ (span: Span[F]) =>
            for {
              pn <- preBind(span)
              _  <- send(ExecuteMessage(pn.value, 0))
              _  <- send(Flush)
              _  <- postBind
              c  <- postExec
            } yield new Protocol.CommandPortal[F, A](pn, statement, args, argsOrigin) {
              def execute: F[Completion] = c.pure
            }
          }
        } { portal => Close[F](opDuration).apply(portal.id)}

      }

      def query[A, B](
        statement:  Protocol.PreparedQuery[F, A, B],
        args:       A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy,
        initialSize: Int
      ): Resource[F, Protocol.QueryPortal[F, A, B]] = {
        val (preBind, postBind) = bindExchange(statement, args, argsOrigin, redactionStrategy)
        Resource.eval(Deferred[F, Unit]).flatMap { prefetch =>
          Resource.make {
            exchange("bind+execute", opDuration){ (span: Span[F]) =>
              for {
                pn <- preBind(span)
                _  <- span.addAttributes(
                        Attribute("max-rows",  initialSize.toLong),
                        Attribute("portal-id", pn.value)
                      )
                _  <- send(ExecuteMessage(pn.value, initialSize))
                _  <- send(Flush)
                _  <- postBind
                rs <- unroll(statement, args, argsOrigin, redactionStrategy)
              } yield new Protocol.QueryPortal[F, A, B](pn, statement, args, argsOrigin, redactionStrategy) {
                def execute(maxRows: Int): F[List[B] ~ Boolean] = 
                  prefetch.tryGet.flatMap {
                    case None => rs.pure <* prefetch.complete(())
                    case Some(()) => Execute[F](opDuration).apply(this, maxRows)
                  }
              }
            }
          } { portal => Close[F](opDuration).apply(portal.id)}
        }
      }
  }


}
