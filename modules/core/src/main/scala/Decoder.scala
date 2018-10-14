package skunk

import cats._
import skunk.data.Type

trait Decoder[A] { outer =>

  def oids: List[Type]
  def decode(ss: List[Option[String]]): A

  def map[B](f: A => B): Decoder[B] =
    new Decoder[B] {
      def decode(ss: List[Option[String]]) = f(outer.decode(ss))
      val oids = outer.oids
    }

  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      def decode(ss: List[Option[String]]) = {
        val (sa, sb) = ss.splitAt(outer.oids.length)
        (outer.decode(sa), fb.decode(sb))
      }
      val oids = outer.oids ++ fb.oids
    }

  def ~[B](fb: Decoder[B]): Decoder[A ~ B] =
    product(fb)

  def opt: Decoder[Option[A]] =
    new Decoder[Option[A]] {
      val oids = outer.oids
      def decode(ss: List[Option[String]]) =
        if (ss.forall(_.isEmpty)) None
        else Some(outer.decode(ss))
    }

  override def toString =
    s"Decoder(${oids.toList.mkString(", ")})"

}

object Decoder {

  implicit val ApplyDecoder: Apply[Decoder] =
    new Apply[Decoder] {
      def map[A, B](fa: Decoder[A])(f: A => B) = fa map f
      def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]) =
        map(fab.product(fa)) { case (fab, a) => fab(a) }
    }

}