// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.implicits._
import cats.Semigroup
import scodec.{ Attempt, Codec => SCodec }
import scodec.bits._
import scodec.codecs._
import scodec.interop.cats._
import skunk.data.Identifier

/**
 * Definitions of Postgres messages, with binary encoders and decoders. Doc for this package isn't
 * very good yet, but the message formats are well documented at the linked pages below. It's a
 * straightforward mapping.
 *
 * It's probably useful to point out that `Codec`, `Encoder`, and `Decoder` in this packge are from
 * [[http://scodec.org/ scodec]]. They're '''not''' the data types of the same name and same general
 * design that are defined above in the `skunk` packgage. I realize this is confusing, but it
 * shouldn't be a concern for anyone other than people working on the wire protocol, which never
 * changes (heh-heh) so it shouldn't be a big deal.
 *
 * @see [[https://www.postgresql.org/docs/10/static/protocol.html Frontend/Backend Protocol]]
 * @see [[https://www.postgresql.org/docs/10/static/protocol-message-formats.html Message Formats]]
 */
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