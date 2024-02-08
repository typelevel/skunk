// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import net.protocol.BindExecute
import org.typelevel.otel4s.trace.Tracer
import skunk.data.Completion
import skunk.net.MessageSocket
import skunk.net.Protocol.{CommandPortal, PreparedCommand, PreparedQuery, QueryPortal}
import skunk.util.{Namer, Origin, Typer}
import skunk.{RedactionStrategy, ~}

trait Prepare[F[_]] {
  def apply[A](command: skunk.Command[A], ty: Typer): F[PreparedCommand[F, A]]
  def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[PreparedQuery[F, A, B]]
}

object Prepare {

  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer: MonadCancelThrow]
  (describeCache: Describe.Cache[F],
   parseCache: Parse.Cache[F],
   redactionStrategy: RedactionStrategy
  ): Prepare[F] =
    new Prepare[F] {

      override def apply[A](command: skunk.Command[A], ty: Typer): F[PreparedCommand[F, A]] =
        for {
          id <- Parse[F](parseCache).apply(command, ty)
          _  <- Describe[F](describeCache).apply(command, id, ty)
        } yield new PreparedCommand[F, A](id, command) { pc =>
          def bind(args: A, origin: Origin): Resource[F, CommandPortal[F, A]] =
            Bind[F].apply(this, args, origin, redactionStrategy).map {
              new CommandPortal[F, A](_, pc, args, origin) {
                val execute: F[Completion] =
                  Execute[F](redactionStrategy).apply(this)
              }
            }

          def bindExecute(args: A, origin: Origin): F[Completion] =
            BindExecute[F].command(this, args, origin, redactionStrategy)
        }

      override def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[PreparedQuery[F, A, B]] =
        for {
          id <- Parse[F](parseCache).apply(query, ty)
          rd <- Describe[F](describeCache).apply(query, id, ty)
        } yield new PreparedQuery[F, A, B](id, query, rd) { pq =>
          def bind(args: A, origin: Origin): Resource[F, QueryPortal[F, A, B]] =
            Bind[F].apply(this, args, origin, redactionStrategy).map {
              new QueryPortal[F, A, B](_, pq, args, origin, redactionStrategy) {
                def execute(maxRows: Int): F[List[B] ~ Boolean] =
                  Execute[F](this.redactionStrategy).apply(this, maxRows, ty)
              }
            }
        }

      }

}
