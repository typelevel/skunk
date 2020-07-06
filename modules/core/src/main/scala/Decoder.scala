// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.implicits._
import skunk.data.Type
import skunk.util.Twiddler

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
      override def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, B] = outer.decode(offset, ss).map(f)
      override val types: List[Type] = outer.types
    }

  /** Adapt this `Decoder` from twiddle-list type A to isomorphic case-class type `B`. */
  def gmap[B](implicit ev: Twiddler.Aux[B, A]): Decoder[B] =
    map(ev.from)

    /** `Decoder` is semigroupal: a pair of decoders make a decoder for a pair. */
  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      override def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, (A, B)] = {
        val (sa, sb) = ss.splitAt(outer.types.length)
        outer.decode(offset, sa) product fb.decode(offset + outer.length, sb)
      }
      override val types: List[Type] = outer.types ++ fb.types
    }

  /** Shorthand for `product`. */
  def ~[B](fb: Decoder[B]): Decoder[A ~ B] =
    product(fb)

  /** Lift this `Decoder` into `Option`. */
  def opt: Decoder[Option[A]] =
    new Decoder[Option[A]] {
      override val types: List[Type] = outer.types
      override def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, Option[A]] =
        if (ss.forall(_.isEmpty)) Right(None)
        else outer.decode(offset, ss).map(Some(_))
    }

  override def toString =
    s"Decoder(${types.mkString(", ")})"

}

/** @group Companions */
object Decoder {

  /**
   * An error indicating that decoding a value starting at column `offset` and spanning `length`
   * columns failed with reason `error`.
   */
  case class Error(offset: Int, length: Int, message: String, cause: Option[Throwable] = None)
  object Error {
    implicit val EqError: Eq[Error] = Eq.fromUniversalEquals
  }

  implicit val ApplyDecoder: Apply[Decoder] =
    new Apply[Decoder] {
      override def map[A, B](fa: Decoder[A])(f: A => B): Decoder[B] = fa map f
      override def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]): Decoder[B] =
        map(fab.product(fa)) { case (fabb, a) => fabb(a) }
    }

}