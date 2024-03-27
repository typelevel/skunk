// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.effect.Concurrent
import cats.syntax.functor._
import skunk.~
import skunk.RedactionStrategy
import skunk.net.MessageSocket
import skunk.net.Protocol.{ PreparedCommand, PreparedQuery, CommandPortal, QueryPortal }
import skunk.util.{ Origin, Namer }
import skunk.util.Typer
import org.typelevel.otel4s.trace.Tracer

trait Prepare[F[_]] {
  def apply[A](command: skunk.Command[A], ty: Typer): F[PreparedCommand[F, A]]
  def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[PreparedQuery[F, A, B]]
}

object Prepare {

  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer](describeCache: Describe.Cache[F], parseCache: Parse.Cache[F], redactionStrategy: RedactionStrategy)(
    implicit ev: Concurrent[F]
  ): Prepare[F] =
    new Prepare[F] {

      override def apply[A](command: skunk.Command[A], ty: Typer): F[PreparedCommand[F, A]] =
        ParseDescribe[F](describeCache, parseCache).command(command, ty).map { id =>
          new PreparedCommand[F, A](id, command) { pc =>
            def bind(args: A, origin: Origin): Resource[F, CommandPortal[F, A]] =
              BindExecute[F].command(this, args, origin, redactionStrategy)
          }
        }

      override def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[PreparedQuery[F, A, B]] =
        ParseDescribe[F](describeCache, parseCache).apply(query, ty).map { case (id, rd) =>
          new PreparedQuery[F, A, B](id, query, rd) { pq =>
            def bind(args: A, origin: Origin): Resource[F, QueryPortal[F, A, B]] =
              Bind[F].apply(this, args, origin, redactionStrategy).map {
               new QueryPortal[F, A, B](_, pq, args, origin, redactionStrategy) {
                  def execute(maxRows: Int): F[List[B] ~ Boolean] =
                   Execute[F].apply(this, maxRows)
                }
              }
            def bindSized(args: A, origin: Origin, maxRows: Int): Resource[F, QueryPortal[F, A, B]] =
              BindExecute[F].query(this, args, origin, redactionStrategy, maxRows)
          }
        }

      }

}
