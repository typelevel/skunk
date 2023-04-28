// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

private[skunk] trait SkunkPlatform {
  // Provide aliases for tuple polyfills so folks don't need to import org.typelevel.twiddles._

  type Tuple = org.typelevel.twiddles.Tuple
  @inline val Tuple = org.typelevel.twiddles.Tuple

  type EmptyTuple = org.typelevel.twiddles.EmptyTuple
  @inline val EmptyTuple = org.typelevel.twiddles.EmptyTuple

  type *:[A, B <: Tuple] = org.typelevel.twiddles.*:[A, B]
  @inline val *: = org.typelevel.twiddles.*:

  implicit def toTupleOps[T <: Tuple](t: T): TupleOps[T] = new TupleOps(t)
}

final class TupleOps[T <: Tuple](private val self: T) extends AnyVal {
  def *:[A](a: A): A *: T = new shapeless.::(a, self)
}