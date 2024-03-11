// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.exception

import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import cats.effect.IO
import skunk.exception.TooManyParametersException

class TooManyParametersExceptionTest extends SkunkTest {

    def stmt(n: Int) = sql"select 1 where 1 in (${int4.list(n)})".query(int4)

    sessionTest(s"ok with ${Short.MaxValue} parameters.") { s =>
      s.prepare(stmt(Short.MaxValue)).flatMap { _ => IO("ok") }
    }

    sessionTest(s"raise TooManyParametersException when statement contains > ${Short.MaxValue} parameters") { s =>
      s.prepare(stmt(Short.MaxValue + 1)).flatMap { _ => IO.never }
       .assertFailsWith[TooManyParametersException]
       .as("ok")
    }

}
