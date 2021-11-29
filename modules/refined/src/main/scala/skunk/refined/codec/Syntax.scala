// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.refined.codec

import skunk.{ Codec, Encoder, Decoder }
import eu.timepit.refined.api.{ Refined, Validate }


object syntax { 
  implicit class RefineCodecOps[T](val c: Codec[T]) {
    def refine[P](implicit v: Validate[T, P]): Codec[Refined[T, P]] =
      refined.refinedCodec(c)
  }

  implicit class RefineEncoderOps[T](val c: Encoder[T]) {
    def refine[P]: Encoder[Refined[T, P]] =
      refined.refinedEncoder(c)
  }

  implicit class RefineDecoderOps[T](val c: Decoder[T]) {
    def refine[P](implicit v: Validate[T, P]): Decoder[Refined[T, P]] =
      refined.refinedDecoder(c)
  }
}
