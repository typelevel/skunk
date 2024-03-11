// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats.syntax.all._
import cats.Semigroup
import scodec.{ Attempt, Codec => SCodec, Err }
import scodec.bits._
import scodec.codecs._
import scodec.interop.cats._
import skunk.data.Identifier
import scodec.SizeBound
import scodec.DecodeResult
import cats.ContravariantSemigroupal
import scodec.Encoder

/**
 * Definitions of Postgres messages, with binary encoders and decoders. Doc for this package isn't
 * very good yet, but the message formats are well documented at the linked pages below. It's a
 * straightforward mapping.
 *
 * It's probably useful to point out that `Codec`, `Encoder`, and `Decoder` in this package are from
 * [[http://scodec.org/ scodec]]. They're '''not''' the data types of the same name and same general
 * design that are defined above in the `skunk` package. I realize this is confusing, but it
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
      override def combine(a: Attempt[A], b: Attempt[A]): Attempt[A] = (a, b).mapN(_ |+| _)
    }

  val utf8z: SCodec[String] = filtered(
    utf8,
    new SCodec[BitVector] {
      val nul = BitVector.lowByte
      override def sizeBound: SizeBound = SizeBound.unknown
      override def encode(bits: BitVector): Attempt[BitVector] = Attempt.successful(bits ++ nul)
      override def decode(bits: BitVector): Attempt[DecodeResult[BitVector]] =
        bits.bytes.indexOfSlice(nul.bytes) match {
          case -1 => Attempt.failure(Err("Does not contain a 'NUL' termination byte."))
          case i  => Attempt.successful(DecodeResult(bits.take(i * 8L), bits.drop(i * 8L + 8L)))
        }
    }
  ).withToString("utf8z")

  val identifier: SCodec[Identifier] =
    utf8z.exmap(
      s  => Attempt.fromEither(Identifier.fromString(s).leftMap(Err(_))),
      id => Attempt.successful(id.value)
    )

  implicit val EncoderContravariantSemigroupal: ContravariantSemigroupal[Encoder] =
    new ContravariantSemigroupal[Encoder] {

      def product[A, B](fa: Encoder[A], fb: Encoder[B]): Encoder[(A, B)] =
        Encoder { (p : (A, B)) =>
          for {
            x <- fa.encode(p._1)
            y <- fb.encode(p._2)
          } yield x ++ y
        }

      def contramap[A, B](fa: Encoder[A])(f: B => A): Encoder[B] =
        fa.contramap(f)

    }

}