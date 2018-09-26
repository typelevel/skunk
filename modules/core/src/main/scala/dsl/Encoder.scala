package skunk
package dsl

import cats._
import cats.data.Chain

trait Encoder[A] { outer =>

  def encode(a: A): Chain[String]

  def oids: Chain[Type]

  def contramap[B](f: B => A): Encoder[B] =
    new Encoder[B] {
      def encode(b: B) = outer.encode(f(b))
      val oids = outer.oids
    }

  def product[B](fb: Encoder[B]): Encoder[(A, B)] =
    new Encoder[(A, B)] {
      def encode(ab: (A, B)) = outer.encode(ab._1) ++ fb.encode(ab._2)
      val oids = outer.oids ++ fb.oids
    }

  override def toString =
    s"Encoder(${oids.toList.mkString(", ")})"

}

object Encoder {

  implicit val ContravariantSemigroupalEncoder: ContravariantSemigroupal[Encoder] =
    new ContravariantSemigroupal[Encoder] {
      def contramap[A, B](fa: Encoder[A])(f: B => A) = fa contramap f
      def product[A, B](fa: Encoder[A],fb: Encoder[B]) = fa product fb
    }

}
