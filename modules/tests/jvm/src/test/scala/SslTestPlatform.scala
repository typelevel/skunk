// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import skunk._
import natchez.Trace.Implicits.noop

trait SslTestPlatform { self: SslTest =>

  test("successful login with SSL.Trusted (ssl available)") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      ssl      = SSL.Trusted,
    ).use(_ => IO.unit)
  }

  test("failed login with SSL.Trusted (ssl not available)") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      ssl      = SSL.Trusted,
      port     = Port.Trust
    ).use(_ => IO.unit).assertFailsWith[Exception].as("ok") // TODO! Better failure!
  }

  test("successful login with SSL.Trusted.withFallback(true) (ssl not available)") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      ssl      = SSL.Trusted.withFallback(true),
      port     = Port.Trust
    ).use(_ => IO.unit)
  }

}
