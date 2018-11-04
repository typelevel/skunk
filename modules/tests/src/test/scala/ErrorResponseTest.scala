package tests

import cats.effect.IO
import skunk.codec.all._
import skunk.implicits._

/**
 * There are a bunch of places where an `ErrorResponse` might occur, and we need to ensure that we
 * handle all of them and return to an healthy state.
 */
case object ErrorResponseTest extends SkunkTest {

  sessionTest("simple command, syntax error") { s =>
    for {
      _ <- s.execute(sql"foo?".command).assertFailsWithSqlException
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("simple query, syntax error") { s =>
    for {
      _ <- s.execute(sql"foo?".query(int4)).assertFailsWithSqlException
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("prepared query, syntax error") { s =>
    for {
      _ <- s.prepare(sql"foo?".query(int4)).use(_ => IO.unit).assertFailsWithSqlException
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("prepared command, syntax error") { s =>
    for {
      _ <- s.prepare(sql"foo?".command).use(_ => IO.unit).assertFailsWithSqlException
      _ <- s.assertHealthy
    } yield "ok"
  }

  // test("prepared query, bind error") {
  //   fail("Not implemented.")
  // }

  // test("prepared query, bind error") {
  //   fail("Not implemented.")
  // }

}
