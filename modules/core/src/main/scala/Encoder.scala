// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.data.State
import cats.implicits._
import skunk.data.Type
import skunk.util.Typer
import skunk.util.Twiddler

/**
 * Encoder of Postgres text-format data from Scala types.
 * @group Codecs
 */
trait Encoder[A] { outer =>

  protected lazy val empty: List[Option[String]] =
    types.map(_ => None)

  /**
   * Given an initial parameter index, yield a hunk of sql containing placeholders, and a new
   * index.
   */
  def sql: State[Int, String]

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
      override def encode(b: B): List[Option[String]] = outer.encode(f(b))
      override val types: List[Type] = outer.types
      override val sql: State[Int, String] = outer.sql
    }

  /** Adapt this `Encoder` from twiddle-list type A to isomorphic case-class type `B`. */
  def gcontramap[B](implicit ev: Twiddler.Aux[B, A]): Encoder[B] =
    contramap(ev.to)

  /** `Encoder` is semigroupal: a pair of encoders make a encoder for a pair. */
  def product[B](fb: Encoder[B]): Encoder[(A, B)] =
    new Encoder[(A, B)] {
      override def encode(ab: (A, B)): List[Option[String]] = outer.encode(ab._1) ++ fb.encode(ab._2)
      override val types: List[Type] = outer.types ++ fb.types
      override val sql: State[Int, String] = (outer.sql, fb.sql).mapN((a, b) => s"$a, $b")
    }

  /** Shorthand for `product`. */
  def ~[B](fb: Encoder[B]): Encoder[A ~ B] =
    product(fb)

  // todo: implicit evidence that it's not already an option .. can be circumvented but prevents
  // dumb errors
  def opt: Encoder[Option[A]] =
    new Encoder[Option[A]] {
      override def encode(a: Option[A]): List[Option[String]] = a.fold(empty)(outer.encode)
      override val types: List[Type] = outer.types
      override val sql: State[Int, String]   = outer.sql
    }

  /**
   * Derive an encoder for a list of size `n` that expands to a comma-separated list of
   * placeholders.
   */
  def list(n: Int): Encoder[List[A]] =
    new Encoder[List[A]] {
      def encode(as: List[A]) = as.flatMap(outer.encode)
      val types = (0 until n).toList.flatMap(_ => outer.types)
      val sql   = outer.sql.replicateA(n).map(_.mkString(", "))
    }

  /**
   * Derive an equivalent encoder for a row type; i.e., its placeholders will be surrounded by
   * parens.
   */
  def values: Encoder[A] =
    new Encoder[A] {
      def encode(a: A): List[Option[String]] = outer.encode(a)
      val types: List[Type] = outer.types
      val sql: State[Int,String] = outer.sql.map(s => s"($s)")
    }

  // now we can say (int4 ~ varchar ~ bool).row.list(2) to get ($1, $2, $3), ($4, $5, $6)

  override def toString =
    s"Encoder(${types.mkString(", ")})"

}

/** @group Companions */
object Encoder {

  implicit val ContravariantSemigroupalEncoder: ContravariantSemigroupal[Encoder] =
    new ContravariantSemigroupal[Encoder] {
      def contramap[A, B](fa: Encoder[A])(f: B => A) = fa contramap f
      def product[A, B](fa: Encoder[A],fb: Encoder[B]) = fa product fb
    }

}
