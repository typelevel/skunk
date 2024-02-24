// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import cats.arrow.FunctionK

class CursorTest extends SkunkTest {

  case class Data(s: String, n: Int)

  def cursor(s: Session[IO]): Resource[IO, Cursor[IO, Data]] =
    s.prepareR(sql"select name, population from country".query(varchar ~ int4))
     .flatMap(_.cursor(Void).map { c =>
       c.map { case s ~ n => Data(s, n) } .mapK(FunctionK.id) // coverage
     })

  sessionTest("single page fetch") { s =>
    cursor(s).use { c =>
      c.fetch(10).flatMap {
        case (rs, true) => assertEqual("should have 10 rows in this page", rs.length, 10)
        case (_, false) => fail("Expected more pages!")
      }
    }
  }

}