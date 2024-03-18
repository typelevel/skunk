// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.syntax.flatMap._
import cats.syntax.functor._
import skunk.~
import skunk.RedactionStrategy
import skunk.data.Completion
import skunk.net.MessageSocket
import skunk.net.Protocol.{ PreparedCommand, PreparedQuery, CommandPortal, QueryPortal }
import skunk.util.{ Origin, Namer }
import skunk.util.Typer
import org.typelevel.otel4s.trace.Tracer
import cats.effect.kernel.MonadCancel

trait Prepare[F[_]] {
  def apply[A](command: skunk.Command[A], ty: Typer): F[PreparedCommand[F, A]]
  def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[PreparedQuery[F, A, B]]
}

object Prepare {

  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer](describeCache: Describe.Cache[F], parseCache: Parse.Cache[F], redactionStrategy: RedactionStrategy)(
    implicit ev: MonadCancel[F, Throwable]
  ): Prepare[F] =
    new Prepare[F] {

      override def apply[A](command: skunk.Command[A], ty: Typer): F[PreparedCommand[F, A]] =
        Describe[F](describeCache, parseCache).apply(command, ty).map { id =>
          new PreparedCommand[F, A](id, command) { pc =>
            def bind(args: A, origin: Origin): Resource[F, CommandPortal[F, A]] =
              Bind[F].apply(this, args, origin, redactionStrategy).map {
                new CommandPortal[F, A](_, pc, args, origin) {
                  val execute: F[Completion] =
                    Execute[F](redactionStrategy).apply(this)
                }
              }
          }
        }

      override def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[PreparedQuery[F, A, B]] =
        for {
          id <- Parse[F](parseCache).apply(query, ty)
          rd <- Describe[F](describeCache, parseCache).apply(query, id, ty)
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
