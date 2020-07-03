// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.implicits._
import skunk.data.Type

trait NumericCodecs {

  // TODO: catch exceptions on these

  val int2: Codec[Short] = Codec.simple(_.toString, _.toShort.asRight, Type.int2)
  val int4: Codec[Int]   = Codec.simple(_.toString, _.toInt.asRight,   Type.int4)
  val int8: Codec[Long]  = Codec.simple(_.toString, _.toLong.asRight,  Type.int8)

  val numeric: Codec[BigDecimal] = Codec.simple(_.toString, BigDecimal(_).asRight, Type.numeric)
  def numeric(precision: Int, scale: Int = 0): Codec[BigDecimal] = Codec.simple(_.toString, BigDecimal(_).asRight, Type.numeric(precision, scale))

  val float4: Codec[Float]   = Codec.simple(_.toString, _.toFloat.asRight, Type.float4)
  val float8: Codec[Double]  = Codec.simple(_.toString, _.toDouble.asRight, Type.float8)

}

object numeric extends NumericCodecs