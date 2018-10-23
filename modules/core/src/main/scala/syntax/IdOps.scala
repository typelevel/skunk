package skunk
package syntax

final class IdOps[A](a: A) {
  def ~[B](b: B): A ~ B = (a, b)
}

trait ToIdOps {
  implicit def toIdOps[A](a: A): IdOps[A] =
    new IdOps(a)
}

object id extends ToIdOps