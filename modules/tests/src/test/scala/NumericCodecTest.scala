// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.implicits._
import skunk.codec.all._

/** Test that we can round=trip values via codecs. */
case object NumericCodecTest extends CodecTest {

  // Boolean
  codecTest(bool)(true, false)

  // Integral
  codecTest(int2)(Short.MinValue, -1, 0, 1, Short.MaxValue)
  codecTest(int4)(Int.MinValue, -1, 0, 1, Int.MaxValue)
  codecTest(int8)(Long.MinValue, -1, 0, 1, Long.MaxValue)

  // Not exactly Double … extents go to infinity
  codecTest(float8)(Double.NegativeInfinity, -1, 0, Double.MinPositiveValue, 1, Double.PositiveInfinity)
  specialValueTest("NaN", float8)(Double.NaN, _.isNaN)

  // Not exactly Float … extents go to infinity and MinPositiveValue gets truncated
  codecTest(float4)(Float.NegativeInfinity, -1, 0, 1, Float.PositiveInfinity)
  specialValueTest("NaN", float4)(Float.NaN, _.isNaN)

}
