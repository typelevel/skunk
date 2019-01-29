// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.effect._
import cats.implicits._

/** A leaked resource and its cleanup program. */
sealed abstract case class Leak[F[_], A](value: A, release: F[Unit])

object Leak {
  def of[F[_]: Bracket[?[_], Throwable], A](rfa: Resource[F, A]): F[Leak[F, A]] =
    rfa.allocated.map(t => new Leak(t._1, t._2) {})
}



