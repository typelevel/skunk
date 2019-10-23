// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.generic.syntax

import skunk.Decoder
import skunk.generic.util.Twiddler

final class DecoderOps[A](private val codec: Decoder[A]) extends AnyVal {
  def gmap[B](implicit ev: Twiddler.Aux[B, A]): Decoder[B] =
    codec.map(ev.from)
}

trait ToDecoderOps {
  implicit def toDecoderOps[A](codec: Decoder[A]): DecoderOps[A] =
    new DecoderOps(codec)
}

object decoder extends ToDecoderOps
