// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import cats.syntax.all._
import fs2.io.net.tls.SSLException
import natchez.Trace.Implicits.noop
import skunk._
import fs2.CompositeFailure

class SslTest extends ffstest.FTest with SslTestPlatform {

  object Port {
    val Invalid = 5431
    val MD5     = 5432
    val Trust   = 5433
  }

  test("successful login with SSL.None (ssl available)") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      ssl      = SSL.None,
    ).use(_ => IO.unit)
  }

  test("failed login with SSL.System (ssl available)") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      ssl      = SSL.System,
    ).use(_ => IO.unit)
      .adaptError {
        case ex @ CompositeFailure(head, tail) =>
          tail.prepend(head).collectFirst({ case ex: SSLException => ex }).getOrElse(ex)
        }
      .assertFailsWith[SSLException].as("sigh") // TODO! Better failure!
  }

  test("successful login with SSL.None (ssl not available)") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      ssl      = SSL.None,
      port     = Port.Trust
    ).use(_ => IO.unit)
  }

  test("SSL.None cannot produce an SSLContext") {
    for {
      ex <- SSL.None.tlsContext[IO].assertFailsWith[Exception]
      _  <- assertEqual("failure message", ex.getMessage, "SSL.None: cannot create a TLSContext.")
    } yield "ok"
  }

}
