// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.implicits._
import skunk.data.Type
import skunk.util.Typer

/**
 * Encoder of Postgres text-format data from Scala types.
 * @group Codecs
 */
trait Encoder[A] { outer =>

  protected lazy val empty: List[Option[String]] =
    types.map(_ => None)

  /**
   * Encode a value of type `A`, yielding a list of Postgres text-formatted strings, lifted to
   * `Option` to handle `NULL` values. Encoding failures raise unrecoverable errors.
   */
  def encode(a: A): List[Option[String]]

  /** Types of encoded fields, in order. */
  def types: List[Type]

  /** Oids of types, or mismatches. */
  def oids(ty: Typer): Either[List[(Type, Option[Int])], List[Int]] = {
    val ots = types.map(ty.oidForType)
    ots.sequence match {
      case Some(os) => os.asRight
      case None     => types.zip(ots).asLeft
    }
  }

  /** Contramap inputs from a new type `B`, yielding an `Encoder[B]`. */
  def contramap[B](f: B => A): Encoder[B] =
    new Encoder[B] {
      def encode(b: B) = outer.encode(f(b))
      val types = outer.types
    }

  /** `Encoder` is semigroupal: a pair of encoders make a encoder for a pair. */
  def product[B](fb: Encoder[B]): Encoder[(A, B)] =
    new Encoder[(A, B)] {
      def encode(ab: (A, B)) = outer.encode(ab._1) ++ fb.encode(ab._2)
      val types = outer.types ++ fb.types
    }

  /** Shorthand for `product`. */
  def ~[B](fb: Encoder[B]): Encoder[A ~ B] =
    product(fb)

  // todo: implicit evidence that it's not already an option .. can be circumvented but prevents
  // dumb errors
  def opt: Encoder[Option[A]] =
    new Encoder[Option[A]] {
      def encode(a: Option[A]) = a.fold(empty)(outer.encode)
      val types = outer.types
    }

  // TODO: decoder, codec
  def list(n: Int): Encoder[List[A]] =
    new Encoder[List[A]] {
      def encode(as: List[A]) = as.flatMap(outer.encode)
      val types = (0 until n).toList.flatMap(_ => outer.types)
    }

  override def toString =
    s"Encoder(${types.toList.mkString(", ")})"

}

/** @group Companions */
object Encoder {

  implicit val ContravariantSemigroupalEncoder: ContravariantSemigroupal[Encoder] =
    new ContravariantSemigroupal[Encoder] {
      def contramap[A, B](fa: Encoder[A])(f: B => A) = fa contramap f
      def product[A, B](fa: Encoder[A],fb: Encoder[B]) = fa product fb
    }

}
