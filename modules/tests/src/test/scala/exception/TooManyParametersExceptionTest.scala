package tests.exception

import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import cats.effect.IO
import skunk.exception.TooManyParametersException

case object TooManyParametersExceptionTest extends SkunkTest {

    def stmt(n: Int) = sql"select 1 where 1 in (${int4.list(n)})".query(int4)

    sessionTest(s"ok with ${Short.MaxValue} parameters.") { s =>
      s.prepare(stmt(Short.MaxValue)).use { _ => IO("ok") }
    }

    sessionTest(s"raise TooManyParametersException when statement contains > ${Short.MaxValue} parameters") { s =>
      s.prepare(stmt(Short.MaxValue + 1)).use { _ => IO.never }
       .assertFailsWith[TooManyParametersException]
    }

}
