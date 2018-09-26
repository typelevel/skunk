package skunk
package dsl

import cats._
import cats.data.Chain

trait Codec[A] extends Encoder[A] with Decoder[A] { outer =>

  def asEncoder: Encoder[A] = this
  def asDecoder: Decoder[A] = this

  def product[B](fb: Codec[B]): Codec[(A, B)] =
    new Codec[(A, B)] {
      val pe = outer.asEncoder product fb.asEncoder
      val pd = outer.asDecoder product fb.asDecoder
      def encode(ab: (A, B)) = pe.encode(ab)
      def decode(ss: List[String]) = pd.decode(ss)
      def oids = outer.oids ++ fb.oids
    }

  def imap[B](f: A => B)(g: B => A): Codec[B] =
    Codec(b => encode(g(b)), ss => f(decode(ss)), oids)

  override def toString =
    s"Codec(${oids.toList.mkString(", ")})"

}

object Codec {

  def apply[A](encode0: A => Chain[String], decode0: List[String] => A, oids0: Chain[Type]): Codec[A] =
    new Codec[A] {
      def encode(a: A) = encode0(a)
      def decode(ss: List[String]) = decode0(ss)
      def oids = oids0
    }

  def single[A](encode: A => String, decode: String => A, oid: Type): Codec[A] =
    apply(a => Chain(encode(a)), ss => decode(ss.head), Chain(oid))

  implicit val InvariantSemigroupalCodec: InvariantSemigroupal[Codec] =
    new InvariantSemigroupal[Codec] {
      def imap[A, B](fa: Codec[A])(f: A => B)(g: B => A) = fa.imap(f)(g)
      def product[A, B](fa: Codec[A],fb: Codec[B]) = fa product fb
    }

  val bit: Codec[Boolean] =
    single(
      b => if (b) "t" else "f",
      { case "t" => true ; case "f" => false },
      Type.bit
    )

  val int2: Codec[Short] = single(_.toString, _.toShort, Type.int2)
  val int4: Codec[Int]   = single(_.toString, _.toInt,   Type.int4)
  val int8: Codec[Long]  = single(_.toString, _.toLong,  Type.int8)

}

