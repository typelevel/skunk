// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.syntax.all._
import java.util.UUID
import skunk.data.Type

trait UuidCodec {

  val uuid: Codec[UUID] =
    Codec.simple[UUID](
      u => u.toString,
      s => Either.catchOnly[IllegalArgumentException](UUID.fromString(s)).leftMap(_.getMessage),
      Type.uuid
    )

}

object uuid extends UuidCodec