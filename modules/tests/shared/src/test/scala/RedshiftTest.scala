// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import skunk._
import skunk.exception.StartupException
import org.typelevel.otel4s.trace.Tracer

class RedshiftTest extends ffstest.FTest {

  implicit val tracer: Tracer[IO] = Tracer.noop

  object X86ArchOnly extends munit.Tag("X86ArchOnly")

  test("redshift - successfully connect".tag(X86ArchOnly)) {
    Session.single[IO](
      host = "localhost",
      user = "postgres",
      database = "postgres",
      password = None,
      port = 5439, // redshift port
      parameters = Session.DefaultConnectionParameters - "IntervalStyle"
    ).use(_ => IO.unit)
  }

  test("redshift - cannot connect with default params".tag(X86ArchOnly)) {
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
