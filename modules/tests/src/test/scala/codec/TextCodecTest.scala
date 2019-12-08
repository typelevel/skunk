// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.implicits._
import skunk.codec.all._
import skunk.implicits._

case object TextCodecTest extends CodecTest {

  // varchar
  codecTest(varchar)("", "a", "ab", "foo", "föf", "🔥 and 🌈", "مرحبا", "שלום", "你好", "';--'")
  codecTest(varchar(3))("", "a", "ab", "foo", "föf", "🔥 a", "مرح", "שלו", "你好", "';'")
  sessionTest("varchar(3) (trimming)") { s =>
    for {
      a <- s.unique(sql"select 'abcdef'::varchar(3)".query(varchar(3)))
      _ <- assertEqual("value should be trimmed to 3 chars", a, "abc")
    } yield ()
  }

  // bpchar
  codecTest(bpchar)("", "a", "ab", "foo", "föf", "🔥 and 🌈", "مرحبا", "שלום", "你好", "';--'")
  codecTest(bpchar(3))("   ", "  a", " ab", "foo", "föf", "🔥 a", "مرح", "שלו", " 你好", "';'")
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

}


