// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._
import skunk.implicits._
import skunk.Arr

class TextCodecTest extends CodecTest {

  // varchar
  roundtripTest(varchar)("", "a", "ab", "foo", "föf", "🔥 and 🌈", "مرحبا", "שלום", "你好", "';--'")
  roundtripTest(varchar(3))("", "a", "ab", "foo", "föf", "🔥 a", "مرح", "שלו", "你好", "';'")
  sessionTest("varchar(3) (trimming)") { s =>
    for {
      a <- s.unique(sql"select 'abcdef'::varchar(3)".query(varchar(3)))
      _ <- assertEqual("value should be trimmed to 3 chars", a, "abc")
    } yield ()
  }

  // bpchar
  roundtripTest(bpchar)("", "a", "ab", "foo", "föf", "🔥 and 🌈", "مرحبا", "שלום", "你好", "';--'")
  roundtripTest(bpchar(3))("   ", "  a", " ab", "foo", "föf", "🔥 a", "مرح", "שלו", " 你好", "';'")
  sessionTest("bpchar(3) (trimmimg)") { s =>
    for {
      a <- s.unique(sql"select 'abcdef'::bpchar(3)".query(bpchar(3)))
      _ <- assertEqual("value should be trimmed to 3 chars", a, "abc")
    } yield ()
  }
  sessionTest("bpchar(3) (padding)") { s =>
    for {
      a <- s.unique(sql"select 'ab'::bpchar(3)".query(bpchar(3)))
      _ <- assertEqual("value should be padded to 3 chars", a, "ab ")
    } yield ()
  }

  roundtripTest(varchar)("\n")

  // array types
  val Some(arr) = Arr("", "ab", "föf", "🔥 and 🌈", "مرحبا", "שלום", "你好", "';--'", "ab\t\b\\cd", "ab\"cd").reshape(5,1,2)
  roundtripTest(_varchar)(Arr.empty, arr)
  roundtripTest(_bpchar )(Arr.empty, arr)
  roundtripTest(_text   )(Arr.empty, arr)
  roundtripTest(_name   )(Arr.empty, arr)

}


