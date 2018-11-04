// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import skunk.data.Type

/**
 * Decoder of Postgres text-format data into Scala types.
 * @group Codecs
 */
trait Decoder[A] { outer =>

  def types: List[Type]
  def decode(ss: List[Option[String]]): A

  /** Map decoded results to a new type `B`, yielding a `Decoder[B]`. */
  def map[B](f: A => B): Decoder[B] =
    new Decoder[B] {
      def decode(ss: List[Option[String]]) = f(outer.decode(ss))
      val types = outer.types
    }

  /** `Decoder` is semigroupal: a pair of decoders make a decoder for a pair. */
  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      def decode(ss: List[Option[String]]) = {
        val (sa, sb) = ss.splitAt(outer.types.length)
        (outer.decode(sa), fb.decode(sb))
      }
      val types = outer.types ++ fb.types
    }

  /** Shorthand for `product`. */
  def ~[B](fb: Decoder[B]): Decoder[A ~ B] =
    product(fb)

  /** Lift this `Decoder` into `Option`. */
  def opt: Decoder[Option[A]] =
    new Decoder[Option[A]] {
      val types = outer.types
      def decode(ss: List[Option[String]]) =
        if (ss.forall(_.isEmpty)) None
        else Some(outer.decode(ss))
    }

  override def toString =
    s"Decoder(${types.toList.mkString(", ")})"

}

/** @group Companions */
object Decoder {

  implicit val ApplyDecoder: Apply[Decoder] =
    new Apply[Decoder] {
      def map[A, B](fa: Decoder[A])(f: A => B) = fa map f
      def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]) =
        map(fab.product(fa)) { case (fab, a) => fab(a) }
    }

}