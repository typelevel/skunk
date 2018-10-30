package skunk

import cats.Contravariant
import cats.effect.Bracket
import cats.implicits._
import skunk.data.Completion
import skunk.net.ProtoSession

/**
 * A prepared command, valid for the life of its defining `Session`.
 * @group Commands
 */
trait PreparedCommand[F[_], A] {

  def check: F[Unit]

  def execute(args: A): F[Completion]

}

/**
 * @group Commands
 */
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

  def fromCommandAndProtoSession[F[_]: Bracket[?[_], Throwable], A](command: Command[A], proto: ProtoSession[F]) =
    proto.prepare(command).map { pc =>
      new PreparedCommand[F, A] {
        def check = proto.check(pc)
        def execute(args: A) =
          Bracket[F, Throwable].bracket(
            proto.bind(pc, args))(
            proto.execute)(
            proto.close
          )
      }
    }

}
