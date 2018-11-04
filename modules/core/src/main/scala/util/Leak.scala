// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.implicits._

/** A leaked resource and its cleanup program. */
sealed abstract case class Leak[F[_], A](value: A, release: F[Unit])

object Leak {

  private def chain(t: Throwable): Exception =
    new RuntimeException("Resource allocation failed. See cause for details.", t)

  def of[F[_], A](rfa: Resource[F, A])(
    implicit cf: Concurrent[F]
  ): F[Leak[F, A]] =
    for {
      du <- Deferred[F, Unit]
      mv <- MVar[F].empty[Either[Throwable, A]]
      _  <- rfa.use {            a => mv.put(Right(a)) *> du.get }
              .handleErrorWith { e => mv.put(Left(e))  *> du.get }
              .start
      e  <- mv.take
      a  <- e match {
              case Right(a) => cf.pure(new Leak(a, du.complete(())) {})
              case Left(e)  => cf.raiseError(chain(e))
            }
    } yield a

}



