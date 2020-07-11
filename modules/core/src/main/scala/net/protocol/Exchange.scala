// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.implicits._
import cats.effect.implicits._
import cats.effect.concurrent.Semaphore
import cats.effect.Concurrent

trait Exchange[F[_]] {
  def apply[A](fa: F[A]): F[A]
}

object Exchange {
  def apply[F[_]: Concurrent]: F[Exchange[F]] =
    Semaphore[F](1).map { sem =>
      new Exchange[F] {
        override def apply[A](fa: F[A]): F[A] =
        sem.withPermit(fa).uncancelable
      }
    }
}
