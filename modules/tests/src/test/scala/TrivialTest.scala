// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.exception.PostgresErrorException
import skunk.data.Completion
import cats.effect.IO

// Trivial tests, mostly to juice codecov
case object TrivialTest extends ffstest.FTest {
  import SqlState.AmbiguousColumn // arbitrary

  test("SqlState") {

    val t = new PostgresErrorException(
      sql       = "",
      sqlOrigin = None,
      info      = Map('C' -> AmbiguousColumn.code, 'M' -> ""),
      history   = Nil
    )

    t match {
      case AmbiguousColumn(e) => assert(s"unapply", e eq t)
      case _                  => fail("unapply didn't work")
    }

  }

  test("Completion.Unknown") {
    IO(Completion.Unknown("foo"))
  }

}
