// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.exception.PostgresErrorException
import cats.effect.IO

class SqlStateTest extends ffstest.FTest {
  import SqlState._

  def err(ss: SqlState): Throwable =
    new PostgresErrorException(
      sql       = "",
      sqlOrigin = None,
      info      = Map('C' -> ss.code, 'M' -> ""),
      history   = Nil
    )

  test("unapply (success)") {
    val t = err(AmbiguousColumn)
    t match {
      case AmbiguousColumn(e) => assert(s"unapply", e eq t).as("ok")
      case _                  => fail("unapply failed to match")
    }
  }

  test("unapply (fail)") {
    val t = err(UniqueViolation)
    t match {
      case AmbiguousColumn(_) => fail("unapply matched incorrectly")
      case _                  => IO("ok")
    }
  }

}
