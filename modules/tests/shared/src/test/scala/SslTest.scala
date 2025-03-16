// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import fs2.io.net.tls.SSLException
import org.typelevel.otel4s.trace.Tracer
import skunk._

class SslTest extends ffstest.FTest {

  object Port {
    val Invalid = 5431
    val MD5     = 5432
    val Trust   = 5433
  }

  tracedTest("successful login with SSL.Trusted (ssl available)") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withSSL(SSL.Trusted)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("successful login with SSL.None (ssl available)") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withSSL(SSL.None)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("failed login with SSL.System (ssl available)") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withSSL(SSL.System)
      .single
      .use(_ => IO.unit).assertFailsWith[SSLException].as("sigh") // TODO! Better failure!
  }

  tracedTest("failed login with SSL.Trusted (ssl not available)") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withSSL(SSL.Trusted)
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit).assertFailsWith[Exception].as("ok") // TODO! Better failure!
  }

  tracedTest("successful login with SSL.Trusted.withFallback(true) (ssl not available)") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withSSL(SSL.Trusted.withFallback(true))
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("successful login with SSL.None (ssl not available)") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withSSL(SSL.None)
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("SSL.None cannot produce an SSLContext") { _ =>
    for {
      ex <- SSL.None.tlsContext[IO].use_.assertFailsWith[Exception]
      _  <- assertEqual("failure message", ex.getMessage, "SSL.None: cannot create a TLSContext.")
    } yield "ok"
  }

}
