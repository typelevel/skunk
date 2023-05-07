// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.syntax.all._
import scala.deriving.Mirror

class EncoderOps[A <: Tuple](self: Encoder[A]) {

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Encoder[B]): Encoder[B *: A] =
    (other, self).contramapN[B *: A] { case b *: a => (b, a) }
  
  @deprecated("Use .to[P] instead of .pcontramap[P]", "0.6")
  def pcontramap[P <: Product](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = A }
  ): Encoder[P] =
    self.contramap(p => Tuple.fromProductTyped(p))

  // For binary compatibility with Skunk 0.3.1 and prior
  @deprecated("Use .to[P] instead of .pcontramap[P]", "0.6")
  private[skunk] def pcontramap[P <: Product](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= A
  ): Encoder[P] =
    pcontramap(using m.asInstanceOf)
 
}

class EncoderOpsLow[A](self: Encoder[A]) {

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Encoder[B]): Encoder[B *: A *: EmptyTuple] =
    other product self

}

trait ToEncoderOps extends ToEncoderOpsLow {
  implicit def toEncoderOps[A <: Tuple](self: Encoder[A]): EncoderOps[A] =
    new EncoderOps(self)
}

trait ToEncoderOpsLow {
  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] implicit def toEncoderOpsLow[A](self: Encoder[A]): EncoderOpsLow[A] =
    new EncoderOpsLow(self)
}

object encoder extends ToEncoderOps
