// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.syntax.all._
import scodec.bits.ByteVector
import skunk.data.Type
import scodec.bits.BitVector

trait BinaryCodecs {

  val bytea: Codec[Array[Byte]] = Codec.simple[Array[Byte]](
    "\\x" + ByteVector.view(_).toHex,
    z => ByteVector.fromHex(z.substring(2)).map(_.toArray).fold("Cannot decode bytes from HEX String".asLeft[Array[Byte]])(_.asRight[String]),
    Type.bytea)

  val bit: Codec[BitVector] =
    bit(1)

  def bit(length: Int): Codec[BitVector] =
    Codec.simple[BitVector](
      _.toBin,
      BitVector.fromBinDescriptive(_),
      Type.bit(length)
    )

  val varbit: Codec[BitVector] =
    Codec.simple[BitVector](
      _.toBin,
      BitVector.fromBinDescriptive(_),
      Type.varbit
    )

  def varbit(length: Int): Codec[BitVector] =
    Codec.simple[BitVector](
      _.toBin,
      BitVector.fromBinDescriptive(_),
      Type.varbit(length)
    )

}

object binary extends BinaryCodecs
