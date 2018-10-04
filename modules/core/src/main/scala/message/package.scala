package skunk

import cats.implicits._
import cats.Semigroup
import scodec.{ Attempt, Codec => SCodec }
import scodec.bits._
import scodec.codecs._
import scodec.interop.cats._

package object message { module =>

  implicit class CodecOps[A](val self: SCodec[A]) extends AnyVal {
    def applied(a: A): SCodec[Unit] =
      self.encode(a).fold(fail(_), constant(_))
  }

  implicit def attemptSemigroup[A: Semigroup]: Semigroup[Attempt[A]] =
    new Semigroup[Attempt[A]] {
      def combine(a: Attempt[A], b: Attempt[A]) = (a, b).mapN(_ |+| _)
    }

  val utf8z: SCodec[String] =
    (utf8 ~ constant(ByteVector(0))).xmap(_._1, (_, ()))

  val identifier: SCodec[Identifier] =
    cstring.xmap(Identifier.unsafeFromString, _.value) // cstring?

}