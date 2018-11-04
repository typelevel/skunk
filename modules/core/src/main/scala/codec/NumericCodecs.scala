// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import skunk.data.Type

trait NumericCodecs {

  val bit: Codec[Boolean] =
   Codec.simple(
      b => if (b) "t" else "f",
      { case "t" => true ; case "f" => false },
      Type.bit
    )

  val int2: Codec[Short] = Codec.simple(_.toString, _.toShort, Type.int2)
  val int4: Codec[Int]   = Codec.simple(_.toString, _.toInt,   Type.int4)
  val int8: Codec[Long]  = Codec.simple(_.toString, _.toLong,  Type.int8)

  val float8: Codec[Double]  = Codec.simple(_.toString, _.toDouble, Type.float8)

}

object numeric extends NumericCodecs