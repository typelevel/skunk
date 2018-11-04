// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import skunk.data.Type

/**
 * Symmetric encoder and decoder of Postgres text-format data to and from Scala types.
 * @group Codecs
 */
trait Codec[A] extends Encoder[A] with Decoder[A] { outer =>

  /** Forget this value is a `Codec` and treat it as an `Encoder`. */
  def asEncoder: Encoder[A] = this

  /** Forget this value is a `Codec` and treat it as a `Decoder`. */
  def asDecoder: Decoder[A] = this

  /** `Codec` is semigroupal: a pair of codecs make a codec for a pair. */
  def product[B](fb: Codec[B]): Codec[(A, B)] =
    new Codec[(A, B)] {
      val pe = outer.asEncoder product fb.asEncoder
      val pd = outer.asDecoder product fb.asDecoder
      def encode(ab: (A, B)) = pe.encode(ab)
      def decode(ss: List[Option[String]]) = pd.decode(ss)
      def types = outer.types ++ fb.types
    }

  /** Shorthand for `product`. */
  def ~[B](fb: Codec[B]): Codec[A ~ B] =
    product(fb)

  /** Contramap inputs from, and map outputs to, a new type `B`, yielding a `Codec[B]`. */
  def imap[B](f: A => B)(g: B => A): Codec[B] =
    Codec(b => encode(g(b)), ss => f(decode(ss)), types)

  /** Lift this `Codec` into `Option`, where `NONE` is mapped to and from a vector of `NULL`. */
  override def opt: Codec[Option[A]] =
    new Codec[Option[A]] {
      def encode(oa: Option[A]) = oa.fold(empty)(outer.encode)
      def decode(ss: List[Option[String]]) = if (ss.forall(_.isEmpty)) None else Some(outer.decode(ss))
      def types = outer.types
    }

  override def toString =
    s"Codec(${types.toList.mkString(", ")})"

}

/** @group Companions */
object Codec {

  /** @group Constructors */
  def apply[A](encode0: A => List[Option[String]], decode0: List[Option[String]] => A, oids0: List[Type]): Codec[A] =
    new Codec[A] {
      def encode(a: A) = encode0(a)
      def decode(ss: List[Option[String]]) = decode0(ss)
      def types = oids0
    }

  // TODO: mechanism for better error reporting … should report a null at a column index so we can
  // refer back to the row description
  /** @group Constructors */
  def simple[A](encode: A => String, decode: String => A, oid: Type): Codec[A] =
    apply(a => List(Some(encode(a))), ss => decode(ss.head.getOrElse(sys.error("null"))), List(oid))

  /**
   * Codec is an invariant semgroupal functor.
   * @group Typeclass Instances
   */
  implicit val InvariantSemigroupalCodec: InvariantSemigroupal[Codec] =
    new InvariantSemigroupal[Codec] {
      def imap[A, B](fa: Codec[A])(f: A => B)(g: B => A) = fa.imap(f)(g)
      def product[A, B](fa: Codec[A],fb: Codec[B]) = fa product fb
    }

}
