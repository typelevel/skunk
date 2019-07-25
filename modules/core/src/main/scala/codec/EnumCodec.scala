// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import skunk.data.Type

trait EnumCodec {

  def enum[A](encode: A => String, decode: String => Option[A], tpe: Type): Codec[A] =
    Codec.simple[A](encode, s => decode(s).toRight(s"${tpe.name}: no such element '$s'"), tpe)

}

object enum extends EnumCodec
