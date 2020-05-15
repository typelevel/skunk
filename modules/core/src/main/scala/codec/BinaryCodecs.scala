// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.implicits._
import scodec.bits.ByteVector
import skunk.data.Type

trait BinaryCodecs {

  val bytea: Codec[Array[Byte]] = Codec.simple[Array[Byte]](
    "\\x" + ByteVector.view(_).toHex,
    z => ByteVector.fromHex(z.substring(2)).map(_.toArray).fold("Cannot decode bytes from HEX String".asLeft[Array[Byte]])(_.asRight[String]),
    Type.bytea)

}

object binary extends BinaryCodecs
