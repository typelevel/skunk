// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.{ Contravariant, ~> }
import cats.effect.Bracket
import skunk.data.Completion
import skunk.net.Protocol
import skunk.util.Origin

/**
 * A prepared command, valid for the life of its defining `Session`.
 * @group Session
 */
trait PreparedCommand[F[_], A] { outer =>

  def execute(args: A)(implicit origin: Origin): F[Completion]

  /**
   * Transform this `PreparedCommand` by a given `FunctionK`.
   * @group Transformations
   */
  def mapK[G[_]](fk: F ~> G): PreparedCommand[G, A] =
    new PreparedCommand[G, A] {
      def execute(args: A)(implicit origin: Origin): G[Completion] = fk(outer.execute(args))
    }

}

/** @group Companions */
object PreparedCommand {

  /** `PreparedCommand[F, ?]` is a contravariant functor for all `F`. */
  implicit def contravariantPreparedCommand[F[_]]: Contravariant[PreparedCommand[F, ?]] =
    new Contravariant[PreparedCommand[F, ?]] {
      def contramap[A, B](fa: PreparedCommand[F,A])(f: B => A) =
        new PreparedCommand[F, B] {
          def execute(args: B)(implicit origin: Origin) = fa.execute(f(args))
        }
    }

  def fromProto[F[_]: Bracket[?[_], Throwable], A](pc: Protocol.PreparedCommand[F, A]) =
    new PreparedCommand[F, A] {
      def execute(args: A)(implicit origin: Origin) =
        pc.bind(args, origin).use(_.execute)
    }

}
