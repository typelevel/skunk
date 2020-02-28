// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect.Resource

package object util {

  type Pool[F[_], A] = Resource[F, Resource[F, A]]

  val LazyList = Stream

}