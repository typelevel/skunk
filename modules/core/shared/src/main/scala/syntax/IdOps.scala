// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

final class IdOps[A](a: A) {
  @deprecated("Use regular tuples or twiddle tuples (*:) instead", "0.6")
  def ~[B](b: B): A ~ B = (a, b)
}

trait ToIdOps {
  implicit def toIdOps[A](a: A): IdOps[A] =
    new IdOps(a)
}

object id extends ToIdOps