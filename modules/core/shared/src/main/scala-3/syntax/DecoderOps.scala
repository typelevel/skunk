// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.syntax.all._
import scala.deriving.Mirror

class DecoderOps[A <: Tuple](self: Decoder[A]) {

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Decoder[B]): Decoder[B *: A] =
    (other, self).mapN(_ *: _)

  // For binary compatibility with Skunk 0.5 and prior
  @deprecated("Use .to[P] instead of .pmap[P]", "0.6")
  def pmap[P <: Product](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = A }
  ): Decoder[P] =
    self.map(m.fromProduct)

  // For binary compatibility with Skunk 0.3.1 and prior
  @deprecated("Use .to[P] instead of .pmap[P]", "0.6")
  private[skunk] def pmap[P <: Product](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= A
  ): Decoder[P] =
    pmap(using m.asInstanceOf)

}

class DecoderOpsLow[A](self: Decoder[A]) {

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Decoder[B]): Decoder[B *: A *: EmptyTuple] =
    other product self

}

trait ToDecoderOps extends ToDecoderOpsLow {
  implicit def toDecoderOps[A <: Tuple](self: Decoder[A]): DecoderOps[A] =
    new DecoderOps(self)
}

trait ToDecoderOpsLow {
  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] implicit def toDecoderOpsLow[A](self: Decoder[A]): DecoderOpsLow[A] =
    new DecoderOpsLow(self)
}

object decoder extends ToDecoderOps
