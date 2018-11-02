package tests

import skunk.codec.all._
import skunk.implicits._
import utest.{ assert => _, _ }

object ErrorResponseSuite extends SkunkSuite {
  val tests = Tests {

    'simple - {

      'command - unsafeRunSession { s =>
        for {
          _ <- s.execute(sql"foo?".command).assertFailsWithSqlException
          _ <- s.assertHealthy
        } yield "ok"
      }

      'query - unsafeRunSession { s =>
        for {
          _ <- s.execute(sql"foo?".query(int4)).assertFailsWithSqlException
          _ <- s.assertHealthy
        } yield "ok"
      }

    }

  }
}

