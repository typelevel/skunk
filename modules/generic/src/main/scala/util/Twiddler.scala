// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package foo.bar

import shapeless.{ HList, ::, HNil, Generic }
import shapeless.ops.hlist.Init
import shapeless.ops.hlist.Last
import shapeless.ops.hlist.Prepend

/**
 * Witness that type `A` is isomorphic to a left-associated HList formed from pairs; i.e.,
 * A :: B :: C :: D :: HNil ~ (((A, B), C), D)
 */
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

  implicit def inductive[A <: HList, IO <: HList, LO](
    implicit in: Init.Aux[A, IO],
             la: Last.Aux[A, LO],
             tw: Twiddler[IO],
             pp: Prepend.Aux[IO, LO :: HNil, A]
  ): Aux[A, (tw.Out, la.Out)] =
    new Twiddler[A] {
      type Out = (tw.Out, la.Out)
      def from(o: Out): A = tw.from(o._1) :+ o._2
      def to(h: A): Out = (tw.to(in(h)), la(h))
    }

  implicit def generic[A, R](
    implicit ge: Generic.Aux[A, R],
             tw: Twiddler[R]
  ): Aux[A, tw.Out] =
    new Twiddler[A] {
      type Out = tw.Out
      def to(h: A): Out = tw.to(ge.to(h))
      def from(o: Out): A = ge.from(tw.from(o))
    }

}

object Y {

    case class City(id: Int, name: String, code: String, district: String, pop: Int)

    // will not typecheck outside this package
    val t: Twiddler.Aux[City, ((((Int, String), String), String), Int)] = Twiddler[City]

}

