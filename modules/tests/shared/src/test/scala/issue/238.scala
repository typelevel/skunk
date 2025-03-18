// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import cats.effect._
import org.typelevel.otel4s.trace.Tracer
import skunk._

class Test238 extends ffstest.FTest {

  tracedTest("see (https://github.com/functional-streams-for-scala/fs2/pull/1989)") { implicit tracer: Tracer[IO] =>
    Session.Builder[IO]
      .withUserAndPassword("jimmy", "banana")
      .withDatabase("world")
      .withTypingStrategy(TypingStrategy.SearchPath)
      .withSSL(SSL.Trusted.withFallback(true))
      .single
      .use(_ => IO.unit)
  }

}
