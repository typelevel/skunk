// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import enumeratum.{ Enum, EnumEntry }
import skunk.data.Type

trait EnumCodec {

  def `enum`[A](encode: A => String, decode: String => Option[A], tpe: Type): Codec[A] =
    Codec.simple[A](encode, s => decode(s).toRight(s"${tpe.name}: no such element '$s'"), tpe)

  def `enum`[A <: EnumEntry](`enum`: Enum[A], tpe: Type): Codec[A] =
    Codec.simple[A](
       a => a.entryName,
       s => `enum`.withNameOption(s).toRight(s"${`enum`}: no such element '$s'"),
       tpe
    )

}

object `enum` extends EnumCodec