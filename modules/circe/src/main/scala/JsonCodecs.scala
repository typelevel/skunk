// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package circe.codec

import cats.implicits._
import io.circe.{ Json, Encoder => CEncoder, Decoder => CDecoder }
import io.circe.parser.parse
import skunk.data.Type

trait JsonCodecs {

  private def genCodec[A](tpe: Type)(
    implicit encode: CEncoder[A],
             decode: CDecoder[A]
  ): Codec[A] =
    Codec.simple(
      a => encode(a).noSpaces,
      s => parse(s).flatMap(decode.decodeJson(_)).leftMap(_.getMessage),
      tpe
    )

  /** Construct a codec for `A`, coded as Json, mapped to the `json` schema type. */
  def json[A: CEncoder: CDecoder]: Codec[A] = genCodec[A](Type.json)

  /** Construct a codec for `A`, coded as Json, mapped to the `jsonb` schema type. */
  def jsonb[A: CEncoder: CDecoder]: Codec[A] = genCodec[A](Type.jsonb)

  /** Codec for `Json` values, mapped to the `json` schema type. */
  val json: Codec[Json] = json[Json]

  /** Codec for `Json` values, mapped to the `jsonb` schema type. */
  val jsonb: Codec[Json] = jsonb[Json]

}

object json extends JsonCodecs

object all extends JsonCodecs
