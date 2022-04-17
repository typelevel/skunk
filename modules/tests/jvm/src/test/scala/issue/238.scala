// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import cats.effect._
import skunk._
import natchez.Trace.Implicits.noop

class Test238 extends ffstest.FTest {

  test("see (https://github.com/functional-streams-for-scala/fs2/pull/1989)") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      strategy = Strategy.SearchPath,
      ssl      = SSL.Trusted.withFallback(true),
      // debug    = true
    ).apply(natchez.Trace[IO]).use(_ => IO.unit)
  }

}
