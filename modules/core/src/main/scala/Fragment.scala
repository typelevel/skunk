package skunk

import cats.Contravariant

case class Fragment[A](sql: String, encoder: Encoder[A]) {

  def query[B](decoder: Decoder[B]): Query[A, B] =
    Query(sql, encoder, decoder)

  def command: Command[A] =
    Command(sql, encoder)

  def contramap[B](f: B => A): Fragment[B] =
    Fragment(sql, encoder.contramap(f))

}

object Fragment {

  implicit val FragmentContravariant: Contravariant[Fragment] =
    new Contravariant[Fragment] {
      def contramap[A, B](fa: Fragment[A])(f: B => A) =
        fa.contramap(f)
    }

}