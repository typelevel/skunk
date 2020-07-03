// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

trait Exchange[F[_]] {
  def apply[A](fa: F[A]): F[A]
}

