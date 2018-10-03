package skunk

import cats._

trait Codec[A] extends Encoder[A] with Decoder[A] { outer =>

  def asEncoder: Encoder[A] = this
  def asDecoder: Decoder[A] = this

  def product[B](fb: Codec[B]): Codec[(A, B)] =
    new Codec[(A, B)] {
      val pe = outer.asEncoder product fb.asEncoder
      val pd = outer.asDecoder product fb.asDecoder
      def encode(ab: (A, B)) = pe.encode(ab)
      def decode(ss: List[Option[String]]) = pd.decode(ss)
      def oids = outer.oids ++ fb.oids
    }

  def ~[B](fb: Codec[B]): Codec[A ~ B] =
    product(fb)

  def imap[B](f: A => B)(g: B => A): Codec[B] =
    Codec(b => encode(g(b)), ss => f(decode(ss)), oids)

  override def toString =
    s"Codec(${oids.toList.mkString(", ")})"

  override def opt: Codec[Option[A]] =
    new Codec[Option[A]] {
      def encode(oa: Option[A]) = oa.fold(empty)(outer.encode)
      def decode(ss: List[Option[String]]) = if (ss.forall(_.isEmpty)) None else Some(outer.decode(ss))
      def oids = outer.oids
    }

}

object Codec extends Codecs {

  def apply[A](encode0: A => List[Option[String]], decode0: List[Option[String]] => A, oids0: List[Type]): Codec[A] =
    new Codec[A] {
      def encode(a: A) = encode0(a)
      def decode(ss: List[Option[String]]) = decode0(ss)
      def oids = oids0
    }

  // TODO: mechanism for better error reporting … should report a null at a column index so we can
  // refer back to the row description
  def simple[A](encode: A => String, decode: String => A, oid: Type): Codec[A] =
    apply(a => List(Some(encode(a))), ss => decode(ss.head.getOrElse(sys.error("null"))), List(oid))

  implicit val InvariantSemigroupalCodec: InvariantSemigroupal[Codec] =
    new InvariantSemigroupal[Codec] {
      def imap[A, B](fa: Codec[A])(f: A => B)(g: B => A) = fa.imap(f)(g)
      def product[A, B](fa: Codec[A],fb: Codec[B]) = fa product fb
    }

}

trait Codecs { this: Codec.type =>

  val bit: Codec[Boolean] =
   simple(
      b => if (b) "t" else "f",
      { case "t" => true ; case "f" => false },
      Type.bit
    )

  val int2: Codec[Short] = simple(_.toString, _.toShort, Type.int2)
  val int4: Codec[Int]   = simple(_.toString, _.toInt,   Type.int4)
  val int8: Codec[Long]  = simple(_.toString, _.toLong,  Type.int8)

  // TODO: ESCAPE!!!
  val varchar: Codec[String] = simple(_.toString, _.toString, Type.varchar)
  val name:    Codec[String] = simple(_.toString, _.toString, Type.name)
  val bpchar:  Codec[String] = simple(_.toString, _.toString, Type.bpchar)

}
