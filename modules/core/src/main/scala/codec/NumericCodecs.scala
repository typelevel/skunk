// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import skunk.data.{Arr, Type}

trait NumericCodecs {

  def safe[A](f: String => A): String => Either[String, A] = s =>
    try Right(f(s))
    catch { case _: NumberFormatException => Left(s"Invalid: $s")}

  val int2: Codec[Short] = Codec.simple(_.toString, safe(_.toShort), Type.int2)
  val int4: Codec[Int]   = Codec.simple(_.toString, safe(_.toInt),   Type.int4)
  val int8: Codec[Long]  = Codec.simple(_.toString, safe(_.toLong),  Type.int8)

  val numeric: Codec[BigDecimal] = Codec.simple(_.toString, safe(BigDecimal(_)), Type.numeric)
  def numeric(precision: Int, scale: Int = 0): Codec[BigDecimal] = Codec.simple(_.toString, safe(BigDecimal(_)), Type.numeric(precision, scale))

  val float4: Codec[Float]   = Codec.simple(_.toString, safe(_.toFloat), Type.float4)
  val float8: Codec[Double]  = Codec.simple(_.toString, safe(_.toDouble), Type.float8)

  // Array types
  val _int2: Codec[Arr[Short]] = Codec.array(_.toString, safe(_.toShort), Type._int2)
  val _int4: Codec[Arr[Int]]   = Codec.array(_.toString, safe(_.toInt),   Type._int4)
  val _int8: Codec[Arr[Long]]  = Codec.array(_.toString, safe(_.toLong),  Type._int8)
  val _numeric: Codec[Arr[BigDecimal]] = Codec.array(_.toString, safe(BigDecimal(_)), Type._numeric)
  val _float4: Codec[Float]   = Codec.simple(_.toString, safe(_.toFloat), Type._float4)
  val _float8: Codec[Double]  = Codec.simple(_.toString, safe(_.toDouble), Type._float8)

}

object numeric extends NumericCodecs