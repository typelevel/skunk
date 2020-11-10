// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import shapeless.{ HList, ::, HNil, Generic }
import shapeless.ops.hlist.Init
import shapeless.ops.hlist.Last
import shapeless.ops.hlist.Prepend
import scala.annotation.implicitNotFound

/**
 * Witness that type `A` is isomorphic to a left-associated HList formed from pairs; i.e.,
 * A :: B :: C :: D :: HNil ~ (((A, B), C), D)
 */
@implicitNotFound("Cannot construct a mapping between the source (which must be a twiddle-list type) and the specified target type ${A} (which must be a case class of the same structure).")
trait Twiddler[A] {
  type Out
  def to(h: A): Out
  def from(o: Out): A
}

object Twiddler {

  def apply[H](implicit ev: Twiddler[H]): ev.type = ev

  type Aux[A, O] = Twiddler[A] { type Out = O }

  implicit def base[A]: Aux[A :: HNil, A] =
    new Twiddler[A :: HNil] {
      type Out = A
      def to(a: A :: HNil) = a.head
      def from(o: Out) = o :: HNil
    }

  implicit def inductive[A <: HList, IO <: HList, LO, TO](
    implicit in: Init.Aux[A, IO],
             la: Last.Aux[A, LO],
             tw: Twiddler.Aux[IO, TO],
             pp: Prepend.Aux[IO, LO :: HNil, A]
  ): Aux[A, (TO, LO)] =
    new Twiddler[A] {
      type Out = (TO, LO)
      def from(o: Out): A = tw.from(o._1) :+ o._2
      def to(h: A): Out = (tw.to(in(h)), la(h))
    }

  implicit def generic[A, R, TO](
    implicit ge: Generic.Aux[A, R],
             tw: Twiddler.Aux[R, TO]
  ): Aux[A, TO] =
    new Twiddler[A] {
      type Out = TO
      def to(h: A): Out = tw.to(ge.to(h))
      def from(o: Out): A = ge.from(tw.from(o))
    }

}
