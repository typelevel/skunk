package skunk

import cats.Contravariant

/** SQL and parameter encoder for a statement that returns no rows. */
final case class Command[A](sql: String, encoder: Encoder[A]) {

  def contramap[B](f: B => A): Command[B] =
    Command(sql, encoder.contramap(f))

}

object Command {

  implicit val CommandContravariant: Contravariant[Command] =
    new Contravariant[Command] {
      def contramap[A, B](fa: Command[A])(f: B => A) =
        fa.contramap(f)
    }

}
