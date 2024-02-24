// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import enumeratum.{ Enum, EnumEntry }
import skunk.data.Type

private[codec] trait EnumCodecCompat {

  def `enum`[A <: EnumEntry](`enum`: Enum[A], tpe: Type): Codec[A] =
    Codec.simple[A](
       a => a.entryName,
       s => `enum`.withNameOption(s).toRight(s"${`enum`}: no such element '$s'"),
       tpe
    )

}
