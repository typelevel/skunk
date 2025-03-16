// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import com.comcast.ip4s.UnknownHostException
import fs2.io.net.ConnectException
import org.typelevel.otel4s.trace.Tracer
import skunk._
import skunk.exception.SkunkException
import skunk.exception.StartupException

class StartupTest extends ffstest.FTest {

  // Different ports for different authentication schemes.
  object Port {
    val Invalid  = 5431
    val MD5      = 5432
    val Trust    = 5433
    val Scram    = 5434
    val Password = 5435
  }

  tracedTest("md5 - successful login") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.MD5)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("md5 - non-existent database") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("blah")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.MD5)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  tracedTest("md5 - missing password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("blah")
      .withUser("jimmy")
      .withPort(Port.MD5)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[SkunkException]
      .flatMap(e => assertEqual("message", e.message, "Password required."))
  }

  tracedTest("md5 - incorrect user") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("blah")
      .withUserAndPassword("frank", "banana")
      .withPort(Port.MD5)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("md5 - incorrect password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("blah")
      .withUserAndPassword("jimmy", "apple")
      .withPort(Port.MD5)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("trust - successful login") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit)
  }

  // TODO: should this be an error?
  tracedTest("trust - successful login, ignored password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("postgres", "ignored")
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("trust - non-existent database") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("bogus")
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  tracedTest("trust - incorrect user") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUser("bogus")
      .withPort(Port.Trust)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28000"))
  }

  tracedTest("scram - successful login") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.Scram)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("scram - non-existent database") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("blah")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.Scram)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  tracedTest("scram - missing password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUser("jimmy")
      .withPort(Port.Scram)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[SkunkException]
      .flatMap(e => assertEqual("message", e.message, "Password required."))
  }

  tracedTest("scram - incorrect user") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("frank", "banana")
      .withPort(Port.Scram)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("scram - incorrect password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "apple")
      .withPort(Port.Scram)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("password - successful login") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.Password)
      .single
      .use(_ => IO.unit)
  }

  tracedTest("password - non-existent database") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("blah")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.Password)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  tracedTest("password - missing password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUser("jimmy")
      .withPort(Port.Password)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[SkunkException]
      .flatMap(e => assertEqual("message", e.message, "Password required."))
  }

  tracedTest("password - incorrect user") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("frank", "banana")
      .withPort(Port.Password)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("password - incorrect password") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "apple")
      .withPort(Port.Password)
      .single
      .use(_ => IO.unit)
      .assertFailsWith[StartupException]
      .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("invalid port") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.Invalid)
      .single
      .use(_ => IO.unit).assertFailsWith[ConnectException]
  }

  tracedTest("invalid host") { implicit tracer: Tracer[IO] =>
    Session.Builder.default[IO]
      .withHost("blergh")
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .withPort(Port.Invalid)
      .single
      .use(_ => IO.unit).assertFailsWith[UnknownHostException]
  }
}
