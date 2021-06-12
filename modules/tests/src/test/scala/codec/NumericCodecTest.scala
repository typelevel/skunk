// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._
import skunk.data.Arr

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

  /* FAILS WITH:

🔥  The statement under consideration was defined
🔥    at /Users/ahjohannessen/Development/Personal/skunk/modules/tests/src/test/scala/codec/CodecTest.scala:66
🔥  
🔥    select $1
🔥  
🔥  The actual and asserted output columns are
🔥  
🔥    ?column?  float8  ->  float4  ── type mismatch
🔥    
  
  */

  // roundtripTest(float4)(Float.NegativeInfinity, -1, 0, 1, Float.PositiveInfinity)
  // roundtripWithSpecialValueTest("NaN", float4)(Float.NaN, _.isNaN)
  // decodeFailureTest(float4, List("x"))

  roundtripTest(numeric)(Double.MinValue, -1, 0, 1, Double.MaxValue)

  /*

🔥  
🔥    select $1::numeric(1,0)
🔥  
🔥  The actual and asserted output columns are
🔥  
🔥    numeric  numeric  ->  numeric(1,0)  ── type mismatch
🔥  
  
  
  */


//  roundtripTest(numeric(1,0))(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  roundtripTest(numeric(1000,999))(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
  /*
🔥  
🔥    select $1::numeric(1000,0)
🔥  
🔥  The actual and asserted output columns are
🔥  
🔥    numeric  numeric  ->  numeric(1000,0)  ── type mismatch  
  */
  //roundtripTest(numeric(1000,0))(Double.MinValue, -1, 0 , 1, Double.MaxValue)
  decodeFailureTest(numeric, List("x"))

/*

🔥  
🔥  Postgres ERROR 0A000 raised in validateArrayDimensions (encoding.go:293)
🔥  
🔥    Problem: Error in argument for $1: unimplemented: 3-dimension arrays not
🔥             supported; only 1-dimension.
🔥       Hint: You have attempted to use a feature that is not yet implemented.
🔥  See:
🔥             https://go.crdb.dev/issue-v/32552/v21.1


*/

  {
    val arr1 = Arr.fromFoldable(List[Short](-1,-2,-3,-4,5,6,7,8))
    val arr2 = arr1//.reshape(2,1,4)
    roundtripTest(_int2)(Arr.empty, arr1, arr2)
  }

  {
    val arr1 = Arr.fromFoldable(List(-1,-2,-3,-4,5,6,7,8))
    val arr2 = arr1//.reshape(2,1,4)
    roundtripTest(_int4)(Arr.empty, arr1, arr2)
  }

  {
    val arr1 = Arr.fromFoldable(List[Long](-1,-2,-3,-4,5,6,7,8))
    val arr2 = arr1//.reshape(2,1,4)
    roundtripTest(_int8)(Arr.empty, arr1, arr2)
  }

}


