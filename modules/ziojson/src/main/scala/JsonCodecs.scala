// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package ziojson.codec

import skunk.data.Type
import zio.json._
import zio.json.ast.Json

trait JsonCodecs {

  private def genCodec[A](tpe: Type)(implicit
                                     encode: JsonEncoder[A],
                                     decode: JsonDecoder[A]
  ): Codec[A] =
    Codec.simple(
      a => a.toJson,
      s => decode.decodeJson(s),
      tpe
    )

  /** Construct a codec for `A`, coded as Json, mapped to the `json` schema type. */
  def json[A: JsonEncoder: JsonDecoder]: Codec[A] = genCodec[A](Type.json)

  /** Construct a codec for `A`, coded as Json, mapped to the `jsonb` schema type. */
  def jsonb[A: JsonEncoder: JsonDecoder]: Codec[A] = genCodec[A](Type.jsonb)

  /** Codec for `Json` values, mapped to the `json` schema type. */
  val json: Codec[Json] = json[Json]

  /** Codec for `Json` values, mapped to the `jsonb` schema type. */
  val jsonb: Codec[Json] = jsonb[Json]

}

object json extends JsonCodecs

object all extends JsonCodecs
