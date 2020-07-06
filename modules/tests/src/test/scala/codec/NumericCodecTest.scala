// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.implicits._
import skunk.codec.all._

/** Test that we can round=trip values via codecs. */
case object NumericCodecTest extends CodecTest {

  // Integral
  roundtripTest(int2)(Short.MinValue, -1, 0, 1, Short.MaxValue)
  decodeFailureTest(int2, List("x"))

  roundtripTest(int4)(Int  .MinValue, -1, 0, 1, Int  .MaxValue)
  decodeFailureTest(int4, List("x"))

  roundtripTest(int8)(Long .MinValue, -1, 0, 1, Long .MaxValue)
  decodeFailureTest(int8, List("x"))

  // Not exactly Double … extents go to infinity
  roundtripTest(float8)(Double.NegativeInfinity, -1, 0, Double.MinPositiveValue, 1, Double.PositiveInfinity)
  roundtripWithSpecialValueTest("NaN", float8)(Double.NaN, _.isNaN)
  decodeFailureTest(float8, List("x"))

  // Not exactly Float … extents go to infinity and MinPositiveValue gets truncated
  roundtripTest(float4)(Float.NegativeInfinity, -1, 0, 1, Float.PositiveInfinity)
  roundtripWithSpecialValueTest("NaN", float4)(Float.NaN, _.isNaN)
  decodeFailureTest(float4, List("x"))

}


