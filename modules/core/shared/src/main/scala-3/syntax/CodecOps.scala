// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.syntax.all._
import scala.deriving.Mirror

class CodecOps[A <: Tuple](self: Codec[A]) {

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Codec[B]): Codec[B *: A] =
    (other, self).imapN((b, a) => b *: a)(t => (t.head, t.tail))

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Decoder[B]): Decoder[B *: A] =
    (other, self).mapN((b, a) => b *: a)

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Encoder[B]): Encoder[B *: A] =
    (other, self).contramapN(t => (t.head, t.tail))

  // For binary compatibility with Skunk 0.5 and prior
  @deprecated("Use .to[P] instead of .pimap[P]", "0.6")
  def pimap[P <: Product](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = A }
  ): Codec[P] =
    self.imap(m.fromProduct)(p => Tuple.fromProductTyped(p))

  // For binary compatibility with Skunk 0.3.1 and prior
  @deprecated("Use .to[P] instead of .pimap[P]", "0.6")
  private[skunk] def pimap[P <: Product](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= A
  ): Codec[P] =
    pimap(using m.asInstanceOf)
}

class CodecOpsLow[A](self: Codec[A]) {

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Codec[B]): Codec[B *: A *: EmptyTuple] =
    other product self

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Decoder[B]): Decoder[B *: A *: EmptyTuple] =
    other product self

  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] def *:[B](other: Encoder[B]): Encoder[B *: A *: EmptyTuple] =
    other product self
}

trait ToCodecOps extends ToCodecOpsLow {
  implicit def toCodecOps[A <: Tuple](self: Codec[A]): CodecOps[A] =
    new CodecOps(self)
}

trait ToCodecOpsLow {
  // For binary compatibility with Skunk 0.5 and prior
  private[syntax] implicit def toCodecOpsLow[A](self: Codec[A]): CodecOpsLow[A] =
    new CodecOpsLow(self)
}

object codec extends ToCodecOps
