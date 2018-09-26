package skunk
package dsl

import cats._
import cats.data.Chain

trait Decoder[A] { outer =>

  def decode(ss: List[String]): A
  def oids: Chain[Type]

  def map[B](f: A => B): Decoder[B] =
    new Decoder[B] {
      def decode(ss: List[String]) = f(outer.decode(ss))
      val oids = outer.oids
    }

  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      def decode(ss: List[String]) = {
        val (sa, sb) = ss.splitAt(outer.oids.length.toInt)
        (outer.decode(sa), fb.decode(sb))
      }
      val oids = outer.oids ++ fb.oids
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