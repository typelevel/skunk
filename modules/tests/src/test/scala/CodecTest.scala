// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.Eq
import cats.implicits._
import skunk._
import skunk.implicits._

/** Tests that we check if we can round-trip values via codecs. */
trait CodecTest extends SkunkTest {

  def codecTest[A: Eq](codec: Codec[A])(as: A*): Unit =
    sessionTest(s"${codec.types.mkString(", ")}") { s =>
      s.prepare(sql"select $codec".query(codec)).use { ps =>
        as.toList.traverse { a =>
          for {
            aʹ <- ps.unique(a)
            _  <- assertEqual(s"$a", aʹ, a)
          } yield a
        } .map(_.mkString(", "))
      }
    }

  // Test a specific special value like NaN where equals doesn't work
  def specialValueTest[A](name: String, codec: Codec[A])(value: A, isOk: A => Boolean): Unit =
    sessionTest(s"${codec.types.mkString(",")}") { s =>
      s.prepare(sql"select $codec".query(codec)).use { ps =>
        ps.unique(value).flatMap { a =>
          assert(name, isOk(a)).as(name)
        }
      }
    }

}
