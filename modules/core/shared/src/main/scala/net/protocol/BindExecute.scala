// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package net.protocol

import cats.effect.kernel.{MonadCancelThrow, Resource}
import cats.syntax.all.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{Span, Tracer}
import skunk.RedactionStrategy
import skunk.data.Completion
import skunk.exception.*
import skunk.net.Protocol.{PortalId, PreparedCommand}
import skunk.net.{MessageSocket, message}
import skunk.net.protocol.*
import skunk.util.{Namer, Origin}

trait BindExecute[F[_]] {

  def command[A](
                  statement: PreparedCommand[F, A],
                  args: A,
                  argsOrigin: Origin,
                  redactionStrategy: RedactionStrategy): F[Completion]

  //def query[A, B](
  //                 statement: PreparedQuery[F, A, B],
  //                 args: A,
  //                 argsOrigin: Origin,
  //                 redactionStrategy: RedactionStrategy,
  //                 maxRows: Int,
  //                 ty: Typer
  //               ): F[List[B] ~ Boolean]

}

object BindExecute {

  def apply[F[_] : Exchange : MessageSocket : Namer : Tracer: MonadCancelThrow]: BindExecute[F] =
    new BindExecute[F] {

      // https://github.com/tpolecat/skunk/issues/210
      val syncReady: F[Unit] =
        send(message.Sync) *> expect { case message.ReadyForQuery(_) => }

      def command[A](
                      statement: PreparedCommand[F, A],
                      args: A,
                      argsOrigin: Origin,
                      redactionStrategy: RedactionStrategy
                    ): F[Completion] =
        exchange("bind_execute") { (span: Span[F]) =>
          Resource.make(nextName("portal").map(PortalId(_)))(Close[F].apply).use { pn =>
          val ea = statement.statement.encoder.encode(args) // encoded args
          for {
            _ <- span.addAttributes(
              Attribute("arguments", redactionStrategy.redactArguments(ea).map(_.orNull).mkString(",")),
              Attribute("portal-id", pn.value)
            )
            _ <- send(message.Bind(pn.value, statement.id.value, ea.map(_.map(_.value))))
            _ <- send(message.Execute(pn.value, 0))
            _ <- send(message.Flush)
            _ <- flatExpect {
              case message.BindComplete => ().pure[F]
              case message.ErrorResponse(info) =>
                for {
                  hi <- history(Int.MaxValue)
                  _ <- syncReady
                  a <- PostgresErrorException.raiseError[F, Unit](
                    sql = statement.statement.sql,
                    sqlOrigin = Some(statement.statement.origin),
                    info = info,
                    history = hi,
                    arguments = statement.statement.encoder.types.zip(ea),
                    argumentsOrigin = Some(argsOrigin)
                  )
                } yield a
            }
            c <- flatExpect {
              case skunk.net.message.CommandComplete(c) => syncReady.as(c)
              case message.EmptyQueryResponse =>
                syncReady *>
                  new EmptyStatementException(statement.command).raiseError[F, Completion]

              case message.CopyOutResponse(_) =>
                receive.iterateWhile {
                  case message.CommandComplete(_) => true
                  case _ => false
                } *>
                  new CopyNotSupportedException(statement.command).raiseError[F, Completion]

              case message.CopyInResponse(_) =>
                send(message.CopyFail) *>
                  expect { case message.ErrorResponse(_) => }
                syncReady *>
                  new CopyNotSupportedException(statement.command).raiseError[F, Completion]

              case message.ErrorResponse(info) =>
                for {
                  hi <- history(Int.MaxValue)
                  _ <- syncReady
                  redactedArgs = statement.command.encoder.types zip redactionStrategy.redactArguments(ea)
                  a <- PostgresErrorException.raiseError[F, Completion](
                    sql = statement.command.sql,
                    sqlOrigin = Some(statement.command.origin),
                    info = info,
                    history = hi,
                    arguments = redactedArgs,
                    argumentsOrigin = Some(argsOrigin)
                  )
                } yield a
            }
          } yield c
        }}


      //def query[A, B](
      //           statement: PreparedQuery[F, A, B],
      //           args: A,
      //           argsOrigin: Origin,
      //           redactionStrategy: RedactionStrategy,
      //           maxRows: Int,
      //           ty: Typer
      //         ): F[List[B] ~ Boolean] = ???
    }
}
