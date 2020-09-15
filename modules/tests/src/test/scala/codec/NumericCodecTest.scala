// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._
import skunk.Arr

/** Test that we can round=trip values via codecs. */
class NumericCodecTest extends CodecTest {

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

  roundtripTest(numeric)(Double.MinValue, -1, 0, 1, Double.MaxValue)
  roundtripTest(numeric(1,0))(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  roundtripTest(numeric(1000,999))(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  roundtripTest(numeric(1000,0))(Double.MinValue, -1, 0 , 1, Double.MaxValue)
  decodeFailureTest(numeric, List("x"))

  val arr1 = Arr.fromFoldable(List(1,2,3,4,5,6,7,8))
  val Some(arr2) = arr1.reshape(2,1,4)
  roundtripTest(_int4)(Arr.empty, arr1, arr2)

}


