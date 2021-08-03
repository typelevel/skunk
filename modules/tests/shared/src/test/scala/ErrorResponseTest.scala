// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import skunk.codec.all._
import skunk.exception.PostgresErrorException
import skunk.implicits._

/**
 * There are a bunch of places where an `ErrorResponse` might occur, and we need to ensure that we
 * handle all of them and return to an healthy state.
 */
abstract class ErrorResponseTest extends SkunkTest {

  sessionTest("simple command, syntax error") { s =>
    for {
      _ <- s.execute(sql"foo?".command).assertFailsWith[PostgresErrorException]
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("simple query, syntax error") { s =>
    for {
      _ <- s.execute(sql"foo?".query(int4)).assertFailsWith[PostgresErrorException]
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("prepared query, syntax error") { s =>
    for {
      _ <- s.prepare(sql"foo?".query(int4)).use(_ => IO.unit).assertFailsWith[PostgresErrorException]
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("prepared command, syntax error") { s =>
    for {
      _ <- s.prepare(sql"foo?".command).use(_ => IO.unit).assertFailsWith[PostgresErrorException]
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
