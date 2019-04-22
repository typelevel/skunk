// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.implicits._
import skunk.data.Type

/**
 * Decoder of Postgres text-format data into Scala types.
 * @group Codecs
 */
trait Decoder[A] { outer =>

  def types: List[Type]
  def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, A]

  def length: Int = types.length

  /** Map decoded results to a new type `B`, yielding a `Decoder[B]`. */
  def map[B](f: A => B): Decoder[B] =
    new Decoder[B] {
      def decode(offset: Int, ss: List[Option[String]]) = outer.decode(offset, ss).map(f)
      val types = outer.types
    }

  /** `Decoder` is semigroupal: a pair of decoders make a decoder for a pair. */
  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      def decode(offset: Int, ss: List[Option[String]]) = {
        val (sa, sb) = ss.splitAt(outer.types.length)
        outer.decode(offset, sa) product fb.decode(offset + outer.length, sb)
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
      def decode(offset: Int, ss: List[Option[String]]) =
        if (ss.forall(_.isEmpty)) Right(None)
        else outer.decode(offset, ss).map(Some(_))
    }

  override def toString =
    s"Decoder(${types.toList.mkString(", ")})"

}

/** @group Companions */
object Decoder {

  /**
   * An error indicating that decoding a value starting at column `offset` and spanning `length`
   * columns failed with reason `error`.
   */
  case class Error(offset: Int, length: Int, message: String)

  implicit val ApplyDecoder: Apply[Decoder] =
    new Apply[Decoder] {
      def map[A, B](fa: Decoder[A])(f: A => B) = fa map f
      def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]) =
        map(fab.product(fa)) { case (fab, a) => fab(a) }
    }

}