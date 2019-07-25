// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.MonadError
import skunk.~
import skunk.data.Completion
import skunk.net.MessageSocket
import skunk.net.Protocol.{CommandPortal, PreparedCommand, PreparedQuery, QueryPortal}
import skunk.util.{Namer, Origin}
import skunk.util.Typer
import natchez.Trace

trait Prepare[F[_]] {
  def apply[A](command:  skunk.Command[A], ty:  Typer): Resource[F, PreparedCommand[F, A]]
  def apply[A, B](query: skunk.Query[A, B], ty: Typer): Resource[F, PreparedQuery[F, A, B]]
}

object Prepare {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Namer: Trace]: Prepare[F] =
    new Prepare[F] {

      override def apply[A](command: skunk.Command[A], ty: Typer): Resource[F, PreparedCommand[F, A]] =
        for {
          id <- Parse[F].apply(command, ty)
          _ <- Resource.liftF(Describe[F].apply(command, id, ty))
        } yield new PreparedCommand[F, A](id, command) { pc =>
          def bind(args: A, origin: Origin): Resource[F, CommandPortal[F, A]] =
            Bind[F].apply(this, args, origin).map {
              new CommandPortal[F, A](_, pc, args, origin) {
                val execute: F[Completion] =
                  Execute[F].apply(this)
              }
            }
        }

      override def apply[A, B](query: skunk.Query[A, B], ty: Typer): Resource[F, PreparedQuery[F, A, B]] =
        for {
          id <- Parse[F].apply(query, ty)
          rd <- Resource.liftF(Describe[F].apply(query, id, ty))
        } yield new PreparedQuery[F, A, B](id, query, rd) { pq =>
          def bind(args: A, origin: Origin): Resource[F, QueryPortal[F, A, B]] =
            Bind[F].apply(this, args, origin).map {
              new QueryPortal[F, A, B](_, pq, args, origin) {
                def execute(maxRows: Int): F[List[B] ~ Boolean] =
                  Execute[F].apply(this, maxRows, ty)
              }
            }
        }

    }

}
