// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package scalajs.codec

import scodec.bits.ByteVector
import skunk.data.Type
import scala.scalajs.js.typedarray.Uint8Array

trait BinaryCodecs {

  val uint8array = Codec.simple[Uint8Array](
    "\\x" + ByteVector.view(_).toHex,
    hex => ByteVector.fromHex(hex.substring(2)).map(_.toUint8Array).toRight("Cannot decode bytes from HEX String"),
    Type.bytea
  )


}

object binary extends BinaryCodecs
