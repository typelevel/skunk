// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats._
import cats.implicits._
import skunk.implicits._
import skunk._
import skunk.codec.all._

case object EncoderTest extends SkunkTest {

  testEncoder(int4.asEncoder ~ varchar.asEncoder)
  testEncoder(ContravariantSemigroupal[Encoder].product(int4.asEncoder, varchar.asEncoder)) // same, via ContravariantSemigroupal instance

  def testEncoder(e: Encoder[Int ~ String]): Unit = {

    test("int ~ varchar") {
      val data = e.encode(123 ~ "abc")
      assertEqual("data", data, List(Some("123"), Some("abc")))
    }

    test("(int ~ varchar).opt (some)") {
      val data = e.opt.encode(Some(123 ~ "abc"))
      assertEqual("data", data, List(Some("123"), Some("abc")))
    }

    test("(int ~ varchar).opt (none)") {
      val data = e.opt.encode(None)
      assertEqual("data", data, List(None, None))
    }

  }

}
