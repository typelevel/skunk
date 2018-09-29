package skunk


import cats._

trait Decoder[A] { outer =>

  def decode(ss: List[Option[String]]): A
  def oids: List[Type]

  def map[B](f: A => B): Decoder[B] =
    new Decoder[B] {
      def decode(ss: List[Option[String]]) = f(outer.decode(ss))
      val oids = outer.oids
    }

  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      def decode(ss: List[Option[String]]) = {
        val (sa, sb) = ss.splitAt(outer.oids.length.toInt)
        (outer.decode(sa), fb.decode(sb))
      }
      val oids = outer.oids ++ fb.oids
    }

  def ~[B](fb: Decoder[B]): Decoder[(A, B)] =
    product(fb)

  override def toString =
    s"Decoder(${oids.toList.mkString(", ")})"

  def opt: Decoder[Option[A]] =
    new Decoder[Option[A]] {
      def decode(ss: List[Option[String]]) = if (ss.forall(_.isEmpty)) None else Some(outer.decode(ss))
      val oids = outer.oids
    }

}

object Decoder {

  implicit val ApplyDecoder: Apply[Decoder] =
    new Apply[Decoder] {
      def map[A, B](fa: Decoder[A])(f: A => B) = fa map f
      def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]) =
        map(fab.product(fa)) { case (fab, a) => fab(a) }
    }

}