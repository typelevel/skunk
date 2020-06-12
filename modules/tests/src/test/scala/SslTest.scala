// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import skunk._
import natchez.Trace.Implicits.noop

case object SslTest extends ffstest.FTest {

  test("md5 - successful login with SSL") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      debug    = true,
      ssl      = SSL.Trusted,
    ).use(_ => IO.unit)
  }

}
