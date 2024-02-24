// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.refined.codec

import skunk.{ Codec, Encoder, Decoder }
import eu.timepit.refined.api.{ Refined, Validate }

trait RefinedCodecs {

  def refinedCodec[T, P](codecT: Codec[T])(implicit v: Validate[T, P]): Codec[Refined[T, P]] =
    refType.refTypeCodec[T, P, Refined](codecT)

  def refinedDecoder[T, P](decoderT: Decoder[T])(implicit v: Validate[T, P]): Decoder[Refined[T, P]] =
    refType.refTypeDecoder[T, P, Refined](decoderT)

  def refinedEncoder[T, P](encoderT: Encoder[T]): Encoder[Refined[T, P]] =
    refType.refTypeEncoder[T, P, Refined](encoderT)

}

object refined extends RefinedCodecs