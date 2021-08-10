// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.syntax.all._
import scala.deriving.Mirror

class DecoderOps[A <: Tuple](self: Decoder[A]) {

  def *:[B](other: Decoder[B]): Decoder[B *: A] =
    (other, self).mapN(_ *: _)

  def pmap[P <: Product](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= A
  ): Decoder[P] =
    self.map(m.fromProduct)

}

class DecoderOpsLow[A](self: Decoder[A]) {

  def *:[B](other: Decoder[B]): Decoder[B *: A *: EmptyTuple] =
    other product self

}

trait ToDecoderOps extends ToDecoderOpsLow {
  implicit def toDecoderOps[A <: Tuple](self: Decoder[A]): DecoderOps[A] =
    new DecoderOps(self)
}

trait ToDecoderOpsLow {
  implicit def toDecoderOpsLow[A](self: Decoder[A]): DecoderOpsLow[A] =
    new DecoderOpsLow(self)
}

object decoder extends ToDecoderOps