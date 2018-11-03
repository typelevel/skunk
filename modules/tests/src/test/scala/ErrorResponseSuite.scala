package tests

import skunk.codec.all._
import skunk.implicits._
import utest.{ assert => _, _ }

/**
 * There are a bunch of places where an `ErrorResponse` might occur, and we need to ensure that we
 * handle all of them and return to an healthy state.
 */
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

    // more … prepare and bind for queries and commands
    // using a closed statement or portal
    // committing with no active transaction
    // etc.

  }
}

