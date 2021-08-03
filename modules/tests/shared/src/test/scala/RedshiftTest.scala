// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import skunk._
import skunk.exception.StartupException
import natchez.Trace.Implicits.noop

abstract class RedshiftTest extends ffstest.FTest {

  test("redshift - successfully connect") {
    Session.single[IO](
      host = "localhost",
      user = "postgres",
      database = "postgres",
      password = None,
      port = 5439, // redshift port
      parameters = Session.DefaultConnectionParameters - "IntervalStyle"
    ).use(_ => IO.unit)
  }

  test("redshift - cannot connect with default params") {
    Session.single[IO](
      host = "localhost",
      user = "postgres",
      database = "postgres",
      password = None,
      port = 5439, // redshift port
    ).use(_ => IO.unit)
    .assertFailsWith[StartupException]
  }
}
