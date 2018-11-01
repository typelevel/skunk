package skunk

import cats._
import skunk.data.Type

/**
 * Decoder of Postgres text-format data into Scala types.
 * @group Codecs
 */
trait Decoder[A] { outer =>

  def oids: List[Type]
  def decode(ss: List[Option[String]]): A

  /** Map decoded results to a new type `B`, yielding a `Decoder[B]`. */
  def map[B](f: A => B): Decoder[B] =
    new Decoder[B] {
      def decode(ss: List[Option[String]]) = f(outer.decode(ss))
      val oids = outer.oids
    }

  /** `Decoder` is semigroupal: a pair of decoders make a decoder for a pair. */
  def product[B](fb: Decoder[B]): Decoder[(A, B)] =
    new Decoder[(A, B)] {
      def decode(ss: List[Option[String]]) = {
        val (sa, sb) = ss.splitAt(outer.oids.length)
        (outer.decode(sa), fb.decode(sb))
      }
      val oids = outer.oids ++ fb.oids
    }

  /** Shorthand for `product`. */
  def ~[B](fb: Decoder[B]): Decoder[A ~ B] =
    product(fb)

  /** Lift this `Decoder` into `Option`. */
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

/** @group Companions */
object Decoder {

  implicit val ApplyDecoder: Apply[Decoder] =
    new Apply[Decoder] {
      def map[A, B](fa: Decoder[A])(f: A => B) = fa map f
      def ap[A, B](fab: Decoder[A => B])(fa: Decoder[A]) =
        map(fab.product(fa)) { case (fab, a) => fab(a) }
    }

}