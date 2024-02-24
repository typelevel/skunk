// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats._
import skunk._
import skunk.data.Encoded
import skunk.codec.all._

class EncoderTest extends SkunkTest {

  testEncoder(int4.asEncoder ~ varchar.asEncoder)
  testEncoder(ContravariantSemigroupal[Encoder].product(int4.asEncoder, varchar.asEncoder)) // same, via ContravariantSemigroupal instance

  def testEncoder(e: Encoder[Int ~ String]): Unit = {

    test("int ~ varchar") {
      val data = e.encode((123, "abc"))
      assertEqual("data", data, List(Some(Encoded("123")), Some(Encoded("abc"))))
    }

    test("(int ~ varchar).opt (some)") {
      val data = e.opt.encode(Some((123, "abc")))
      assertEqual("data", data, List(Some(Encoded("123")), Some(Encoded("abc"))))
    }

    test("(int ~ varchar).opt (none)") {
      val data = e.opt.encode(None)
      assertEqual("data", data, List(None, None))
    }

  }

  test("void") {
    val data = Void.codec.encode(Void)
    assertEqual("data", data, Nil)
  }

  test("redaction - unredacted") {
    val data = int4.encode(42)
    assertEquals(data, List(Some(Encoded("42"))))
  }

  test("redaction - redacted") {
    val data = int4.redacted.encode(42)
    assertEquals(data, List(Some(Encoded("42", true))))
  }

  test("redaction - redacted product") {
    val data = (int4 ~ int4).redacted.encode((1, 2))
    assertEquals(data, List(Some(Encoded("1", true)), Some(Encoded("2", true))))
  }

  test("redaction - product of redacted") {
    val data = (int4 ~ int4.redacted).encode((1, 2))
    assertEquals(data, List(Some(Encoded("1", false)), Some(Encoded("2", true))))
  }

  test("redaction - contramap") {
    val data = (int4 ~ int4.redacted).contramap(identity[(Int, Int)]).encode((1, 2))
    assertEquals(data, List(Some(Encoded("1", false)), Some(Encoded("2", true))))
  }

  test("redaction - imap") {
    val data = (int4 ~ int4.redacted).imap(identity)(identity).encode((1, 2))
    assertEquals(data, List(Some(Encoded("1", false)), Some(Encoded("2", true))))
  }

  test("redaction - to") {
    case class Point(x: Int, y: Int)
    val data = (int4 *: int4.redacted).to[Point].encode(Point(1, 2))
    assertEquals(data, List(Some(Encoded("1", false)), Some(Encoded("2", true))))
  }
}
