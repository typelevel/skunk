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
import cats.effect.IO

case object DecoderTest extends SkunkTest {

  testDecoder(int4.asDecoder ~ varchar.asDecoder)
  testDecoder(Apply[Decoder].tuple2(int4.asDecoder, varchar.asDecoder)) // same, via Apply instance

  def testDecoder(d: Decoder[Int ~ String]): Unit = {

    test("int ~ varchar") {
      d.decode(0, List(Some("123"), Some("abc"))) match {
        case Left(value)  => fail(value.message)
        case Right(value) => assertEqual("123 ~ abc", value, 123 ~ "abc")
      }
    }

    test("(int ~ varchar).opt (some)") {
      d.opt.decode(0, List(Some("123"), Some("abc"))) match {
        case Left(value)  => fail(value.message)
        case Right(value) => assertEqual("Some(123 ~ abc)", value, Some(123 ~ "abc"))
      }
    }

    test("(int ~ varchar).opt (none)") {
      d.opt.decode(0, List(None, None)) match {
        case Left(value)  => fail(value.message)
        case Right(value) => assertEqual("None", value, None)
      }
    }

    test("(int ~ varchar).opt (mixed -- error)") {
      d.opt.decode(0, List(Some("123"), None)) match {
        case Left(value)  => assertEqual("error offset", value.offset, 1)
        case Right(value) => fail(s"Shouldn't decode! Got $value")
      }
    }

  }

  test("void (ok)") {
    Void.codec.decode(0, Nil) match {
      case Left(err)  => fail(err.message)
      case Right(value) => assertEqual("void", value, Void)
    }
  }

  test("void (fail)") {
    Void.codec.decode(0, List(None)) match {
      case Left(_)      => "ok".pure[IO]
      case Right(value) => fail(s"expected failure, got $value")
    }
  }

}
