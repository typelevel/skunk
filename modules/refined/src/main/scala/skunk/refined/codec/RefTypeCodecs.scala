// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.refined.codec

import skunk.{ Codec, Encoder, Decoder }
import eu.timepit.refined.api.{ RefType, Validate }

trait RefTypeCodecs {
  def refTypeCodec[T, P, F[_,_]](codecT: Codec[T])(
      implicit validate: Validate[T, P], refType: RefType[F]): Codec[F[T, P]] =
    codecT.eimap[F[T,P]](
      refType.refine[P](_)(validate))(
      refType.unwrap
    )

  def refTypeEncoder[T, P, F[_,_]](writeT: Encoder[T])(implicit refType: RefType[F]): Encoder[F[T,P]] =
    writeT.contramap[F[T,P]](refType.unwrap)

  def refTypeDecoder[T, P, F[_,_]](readT: Decoder[T])(
      implicit validate: Validate[T, P], refType: RefType[F]): Decoder[F[T,P]] =
    readT.emap[F[T,P]](refType.refine[P](_)(validate))
}

object refType extends RefTypeCodecs
