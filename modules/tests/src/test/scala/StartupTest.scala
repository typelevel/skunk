// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import skunk._
import natchez.Trace.Implicits.noop
import skunk.exception.SkunkException
import skunk.exception.StartupException
import java.net.ConnectException

class StartupTest extends ffstest.FTest {

  override def munitIgnore: Boolean = true // Ignoring for now

  // Different ports for different authentication schemes.
  object Port {
    val Invalid = 5431
    val MD5     = 5432
    val Trust   = 5433
    val Scram   = 5434
  }

  test("md5 - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      port     = Port.MD5,
    ).use(_ => IO.unit)
  }

  test("md5 - non-existent database") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "blah",
      password = Some("banana"),
      port     = Port.MD5,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  test("md5 - missing password") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "blah",
      password = None,
      port     = Port.MD5,
    ).use(_ => IO.unit)
     .assertFailsWith[SkunkException]
     .flatMap(e => assertEqual("message", e.message, "Password required."))
  }

  test("md5 - incorrect user") {
    Session.single[IO](
      host     = "localhost",
      user     = "frank",
      database = "world",
      password = Some("banana"),
      port     = Port.MD5,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  test("md5 - incorrect password") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("apple"),
      port     = Port.MD5,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  test("trust - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      port     = Port.Trust,
    ).use(_ => IO.unit)
  }

  // TODO: should this be an error?
  test("trust - successful login, ignored password") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      password = Some("ignored"),
      port     = Port.Trust,
    ).use(_ => IO.unit)
  }

  test("trust - non-existent database") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "bogus",
      port     = Port.Trust,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  test("trust - incorrect user") {
    Session.single[IO](
      host     = "localhost",
      user     = "bogus",
      database = "world",
      port     = Port.Trust,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28000"))
  }

  test("scram - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      port     = Port.Scram
    ).use(_ => IO.unit)
  }

  test("scram - non-existent database") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "blah",
      password = Some("banana"),
      port     = Port.Scram,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  test("scram - missing password") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "blah",
      password = None,
      port     = Port.Scram,
    ).use(_ => IO.unit)
     .assertFailsWith[SkunkException]
     .flatMap(e => assertEqual("message", e.message, "Password required."))
  }

  test("scram - incorrect user") {
    Session.single[IO](
      host     = "localhost",
      user     = "frank",
      database = "world",
      password = Some("banana"),
      port     = Port.Scram,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  test("scram - incorrect password") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("apple"),
      port     = Port.Scram,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  test("invalid port") {
    Session.single[IO](
      host     = "localhost",
      user     = "bob",
      database = "nobody cares",
      port     = Port.Invalid
    ).use(_ => IO.unit).assertFailsWith[ConnectException]
  }

  test("invalid host") {
    Session.single[IO](
      host     = "blergh",
      user     = "bob",
      database = "nobody cares",
    ).use(_ => IO.unit).assertFailsWith[java.net.UnknownHostException]
  }
}
