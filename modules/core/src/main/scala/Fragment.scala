// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.ContravariantSemigroupal
import cats.data.State
import cats.syntax.all._
import skunk.util.Origin

/**
 * A composable, embeddable hunk of SQL and typed parameters (common precursor to `Command` and
 * `Query`). Although it is possible to construct a `Fragment` directly it is more typical to use
 * the `sql` interpolator.
 * @group Statements
 */
final case class Fragment[A](
  parts:   List[Either[String, State[Int, String]]],
  encoder: Encoder[A],
  origin:  Origin
) extends (A => AppliedFragment) {

  lazy val sql: String =
    parts.traverse {
      case Left(s)  => s.pure[State[Int, *]]
      case Right(s) => s
    } .runA(1).value.combineAll

  def query[B](decoder: Decoder[B]): Query[A, B] =
    Query(sql, origin, encoder, decoder)

  def command: Command[A] =
    Command(sql, origin, encoder)

  def contramap[B](f: B => A): Fragment[B] =
    Fragment(parts, encoder.contramap(f), origin)

  def product[B](fb: Fragment[B]): Fragment[(A, B)] =
    Fragment(parts <+> fb.parts, encoder ~ fb.encoder, origin)

  def ~[B](fb: Fragment[B]): Fragment[A ~ B] =
    product(fb)

  def apply(a: A): AppliedFragment =
    AppliedFragment(this, a)

  override def toString: String =
    s"Fragment($sql, $encoder)"

}

/** @group Companions */
object Fragment {

  implicit val FragmentContravariantSemigroupal: ContravariantSemigroupal[Fragment] =
    new ContravariantSemigroupal[Fragment] {
      override def contramap[A, B](fa: Fragment[A])(f: B => A): Fragment[B] = fa.contramap(f)
      override def product[A, B](fa: Fragment[A], fb: Fragment[B]): Fragment[(A, B)] = fa product fb
    }

  private[skunk] def apply(sql: String): Fragment[Void] =
    Fragment(List(Left(sql)), Void.codec, Origin.unknown)


  val empty: Fragment[Void] =
    apply("")

}