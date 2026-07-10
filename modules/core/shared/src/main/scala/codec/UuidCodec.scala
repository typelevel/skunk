// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.syntax.all._
import java.util.UUID
import skunk.data.Type
import skunk.data.Arr

trait UuidCodec {

  val uuid: Codec[UUID] =
    Codec.simple[UUID](_.toString, codec.uuid.parse, Type.uuid)

  def _uuid: Codec[Arr[UUID]] = codec.uuid._uuidImpl
}

object uuid extends UuidCodec {
  private[codec] def parse(s: String): Either[String, UUID] =
      Either.catchOnly[IllegalArgumentException](UUID.fromString(s)).leftMap(_.getMessage)

  private[codec] val _uuidImpl: Codec[Arr[UUID]] =
    Codec.array[UUID](_.toString, parse, Type._uuid)
}
