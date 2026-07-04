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

  private def parse(s: String): Either[String, UUID] =
      Either.catchOnly[IllegalArgumentException](UUID.fromString(s)).leftMap(_.getMessage)

  val uuid: Codec[UUID] =
    Codec.simple[UUID](_.toString, parse, Type.uuid)

  val _uuid: Codec[Arr[UUID]] =
    Codec.array[UUID](_.toString, parse, Type._uuid)
}

object uuid extends UuidCodec
