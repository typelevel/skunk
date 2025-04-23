// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import skunk.Session
import skunk.exception.SkunkException

class SessionTest extends ffstest.FTest {

  test("Invalid host") {
    val e = intercept[SkunkException] {
      Session.Builder[IO].withHost("")
    }
    assertEquals(e.message, """Hostname: "" is not syntactically valid.""")
  }

  test("Invalid port") {
    val e = intercept[SkunkException] {
      Session.Builder[IO].withPort(-1).single.use(_ => IO.unit)
    }
    assertEquals(e.message, "Port: -1 falls out of the allowed range.")
  }
}
