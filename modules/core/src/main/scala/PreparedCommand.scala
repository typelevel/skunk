package skunk

import cats.Contravariant
import cats.effect.Bracket
import skunk.data.Completion
import skunk.net.Protocol

/**
 * A prepared command, valid for the life of its defining `Session`.
 * @group Session
 */
trait PreparedCommand[F[_], A] {
  def check: F[Unit]
  def execute(args: A): F[Completion]
}

/** @group Companions */
object PreparedCommand {

  /** `PreparedCommand[F, ?]` is a contravariant functor for all `F`. */
  implicit def contravariantPreparedCommand[F[_]]: Contravariant[PreparedCommand[F, ?]] =
    new Contravariant[PreparedCommand[F, ?]] {
      def contramap[A, B](fa: PreparedCommand[F,A])(f: B => A) =
        new PreparedCommand[F, B] {
          def check = fa.check
          def execute(args: B) = fa.execute(f(args))
        }
    }

  def fromProto[F[_]: Bracket[?[_], Throwable], A](pc: Protocol.PreparedCommand[F, A]) =
    new PreparedCommand[F, A] {
      def check = pc.check
      def execute(args: A) =
        Bracket[F, Throwable].bracket(pc.bind(args))(_.execute)(_.close)
    }

}
