// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.kernel.Eq
import skunk.codec.all._

class BinaryCodecTest extends CodecTest {

  implicit def arrayEq[A]: Eq[Array[A]] = Eq.instance(_.toList == _.toList)

  val byteArray1: Array[Byte] = "someValue".getBytes("UTF-8")
  val byteArray2: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray

  roundtripTest(bytea)(byteArray1, byteArray2)
  decodeFailureTest(bytea, List("foobar"))

}


