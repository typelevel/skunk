// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.implicits._
import skunk.codec.all._
import skunk.data.BooleanRepresentation._

case object BooleanCodecTest extends CodecTest {
  roundtripTest(bool)(true, false)
  decodeFailureTest(bool, List("xx"))


  roundtripWithSpecialValueTest("true bool as true", bool(True, False))(true, _ == true)
  roundtripWithSpecialValueTest("true bool as t", bool(T, False))(true, _ == true)
  roundtripWithSpecialValueTest("true bool as yes", bool(Yes, False))(true, _ == true)
  roundtripWithSpecialValueTest("true bool as y", bool(Y, False))(true, _ == true)
  roundtripWithSpecialValueTest("true bool as on", bool(On, False))(true, _ == true)
  roundtripWithSpecialValueTest("true bool as 1", bool(`1`, False))(true, _ == true)

  roundtripWithSpecialValueTest("false bool as false", bool(True, False))(false, _ == false)
  roundtripWithSpecialValueTest("false bool as f", bool(True, F))(false, _ == false)
  roundtripWithSpecialValueTest("false bool as no", bool(True, No))(false, _ == false)
  roundtripWithSpecialValueTest("false bool as n", bool(True, N))(false, _ == false)
  roundtripWithSpecialValueTest("false bool as off", bool(True, Off))(false, _ == false)
  roundtripWithSpecialValueTest("false bool as 0", bool(True, `0`))(false, _ == false)

}


