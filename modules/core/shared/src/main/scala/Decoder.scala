// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.syntax.all._
import org.typelevel.twiddles.TwiddleSyntax
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

  /** Map decoded results to a new type `B` or an error, yielding a `Decoder[B]`. */
  def emap[B](f: A => Either[String, B]): Decoder[B] =
    new Decoder[B] {
      override def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, B] =
        outer.decode(offset, ss).flatMap(f(_).leftMap(Decoder.Error(offset, length, _)))
      override val types: List[Type] = outer.types
    }

  /**
   * An equivalent decoder that filters results, failing with a generic error message when the
   * filter condition is not met. For a custom error message use `emap`.
   */
  def filter[B](f: A => Boolean): Decoder[A] =
    emap(a => Either.cond(f(a), a, "Filter condition failed."))

  /** Adapt this `Decoder` from twiddle-list type A to isomorphic case-class type `B`. */
  @deprecated("Use (a *: b *: c).as[CaseClass] instead of (a ~ b ~ c).gmap[CaseClass]", "0.6")
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
object Decoder extends TwiddleSyntax[Decoder] {

  /**
   * An error indicating that decoding a value starting at column `offset` and spanning `length`
   * columns failed with reason `error`.
   */
  case class Error(offset: Int, length: Int, message: String, cause: Option[Throwable] = None)
  object Error {
    implicit val EqError: Eq[Error] = Eq.fromUniversalEquals
  }

  implicit val ApplicativeDecoder: Applicative[Decoder] =
    new Applicative[Decoder] {
      override def map[A, B](fa: Decoder[A])(f: A => B): Decoder[B] = fa map f
      override def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]): Decoder[B] =
        map(fab.product(fa)) { case (fabb, a) => fabb(a) }
      override def pure[A](x: A): Decoder[A] = new Decoder[A] {
        def types: List[Type] = Nil
        def decode(offset: Int, ss: List[Option[String]]): Either[Error, A] = Right(x)
      }
    }

}