package skunk

import cats._
import skunk.data.Type

/**
 * Encoder of Postgres text-format data from Scala types.
 * @group Codecs
 */
trait Encoder[A] { outer =>

  protected lazy val empty: List[Option[String]] =
    oids.map(_ => None)

  def encode(a: A): List[Option[String]]

  def oids: List[Type]

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

  def ~[B](fb: Encoder[B]): Encoder[A ~ B] =
    product(fb)

  // todo: implicit evidence that it's not already an option .. can be circumvented but prevents
  // dumb errors
  def opt: Encoder[Option[A]] =
    new Encoder[Option[A]] {
      def encode(a: Option[A]) = a.fold(empty)(outer.encode)
      val oids = outer.oids
    }

  // TODO: decoder, codec
  def list(n: Int): Encoder[List[A]] =
    new Encoder[List[A]] {
      def encode(as: List[A]) = as.flatMap(outer.encode)
      val oids = (0 until n).toList.flatMap(_ => outer.oids)
    }

  override def toString =
    s"Encoder(${oids.toList.mkString(", ")})"

}

/** @group Codecs */
object Encoder {

  implicit val ContravariantSemigroupalEncoder: ContravariantSemigroupal[Encoder] =
    new ContravariantSemigroupal[Encoder] {
      def contramap[A, B](fa: Encoder[A])(f: B => A) = fa contramap f
      def product[A, B](fa: Encoder[A],fb: Encoder[B]) = fa product fb
    }

}
