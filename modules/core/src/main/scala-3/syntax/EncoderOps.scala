// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.syntax.all._
import scala.deriving.Mirror

class EncoderOps[A <: Tuple](self: Encoder[A]) {

  def *:[B](other: Encoder[B]): Encoder[B *: A] =
    (other, self).contramapN[B *: A] { case b *: a => (b, a) }

  def pcontramap[P <: Product](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= A
  ): Encoder[P] =
    self.contramap(p => i(Tuple.fromProductTyped(p)))

}

class EncoderOpsLow[A](self: Encoder[A]) {

  def *:[B](other: Encoder[B]): Encoder[B *: A *: EmptyTuple] =
    other product self

}

trait ToEncoderOps extends ToEncoderOpsLow {
  implicit def toEncoderOps[A <: Tuple](self: Encoder[A]): EncoderOps[A] =
    new EncoderOps(self)
}

trait ToEncoderOpsLow {
  implicit def toEncoderOpsLow[A](self: Encoder[A]): EncoderOpsLow[A] =
    new EncoderOpsLow(self)
}

object encoder extends ToEncoderOps