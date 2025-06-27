// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import skunk.codec.all._
import cats.Eq

/** Test that we can roundtrip for a 16 length Twiddle List in Scala 3. */
@annotation.nowarn
class LongTwiddleListTest extends CodecTest {

  roundtripTest((int4 ~ varchar ~ int4 ~ varchar ~ int4 ~ varchar ~ int4 ~ varchar ~ int4 ~ varchar ~ int4 ~ varchar ~
    int4 ~ varchar ~ int4 ~ varchar).gimap[Sixteen])(
    Sixteen(1, "1", 1, "1", 1, "1", 1, "1", 1, "1", 1, "1", 1, "1", 1, "1"))

}
case class Sixteen(i1: Int, s1: String, i2: Int, s2: String, i3: Int, s3: String, i4: Int, s4: String,
  i5: Int, s5: String, i6: Int, s6: String, i7: Int, s7: String, i8: Int, s8: String)
object Sixteen {
  implicit val eqSixteen: Eq[Sixteen] = Eq.fromUniversalEquals
}
