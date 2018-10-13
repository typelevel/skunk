package skunk

import cats.Contravariant
import cats.data.State
import cats.implicits._

case class Fragment[A](parts: List[Either[String, Int]], encoder: Encoder[A]) {

  lazy val sql: String =
    parts.traverse {
      case Left(s)  => s.pure[State[Int, ?]]
      case Right(n) => State((i: Int) => (i + n, (i until i + n).map("$" + _).mkString(", ")))
    } .runA(1).value.combineAll

  def query[B](decoder: Decoder[B]): Query[A, B] =
    Query(sql, encoder, decoder)

  def command: Command[A] =
    Command(sql, encoder)

  def contramap[B](f: B => A): Fragment[B] =
    Fragment(parts, encoder.contramap(f))

  override def toString =
    s"Fragment($sql, $encoder)"

}

object Fragment {

  implicit val FragmentContravariant: Contravariant[Fragment] =
    new Contravariant[Fragment] {
      def contramap[A, B](fa: Fragment[A])(f: B => A) =
        fa.contramap(f)
    }

}