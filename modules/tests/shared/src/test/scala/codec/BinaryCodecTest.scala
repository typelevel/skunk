// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.kernel.Eq
import skunk.codec.all._
import scodec.bits.BitVector
import scodec.interop.cats._

class BinaryCodecTest extends CodecTest {

  implicit def arrayEq[A]: Eq[Array[A]] = Eq.instance(_.toList == _.toList)

  // bytea
  val byteArray1: Array[Byte] = "someValue".getBytes("UTF-8")
  val byteArray2: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray

  roundtripTest(bytea)(byteArray1, byteArray2)
  decodeFailureTest(bytea, List("foobar"))

  // Make a bitvector
  def bv(s: String) = BitVector.fromBin(s).get

  // bit, same as bit(1)
  roundtripTest(bit)(bv("0"))
  roundtripTest(bit)(bv("1"))
  decodeFailureTest(bit, List("foobar"))

  // bit(3)
  roundtripTest(bit(3))(bv("101"))
  roundtripWithSpecialValueTest("padding", bit(3), Some("bit(3)"))(bv("1"), _ === bv("100"))
  roundtripWithSpecialValueTest("truncation", bit(3), Some("bit(3)"))(bv("1011"), _ === bv("101"))

  // varbit
  roundtripTest(varbit)(bv(""))
  roundtripTest(varbit)(bv("0"))
  roundtripTest(varbit)(bv("1001001011011"))
  decodeFailureTest(varbit, List("foobar"))

  // varbit(3)
  roundtripTest(varbit(3))(bv(""))
  roundtripTest(varbit(3))(bv("011"))
  roundtripWithSpecialValueTest("truncation", varbit(3), Some("varbit(3)"))(bv("1001001011011"), _ === bv("100"))
  decodeFailureTest(varbit(3), List("foobar"))

}


