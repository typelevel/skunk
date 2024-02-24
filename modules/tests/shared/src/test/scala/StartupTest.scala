// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import com.comcast.ip4s.UnknownHostException
import fs2.io.net.ConnectException
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

  tracedTest("md5 - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      port     = Port.MD5,
    ).use(_ => IO.unit)
  }

  tracedTest("md5 - non-existent database") {
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

  tracedTest("md5 - missing password") {
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

  tracedTest("md5 - incorrect user") {
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

  tracedTest("md5 - incorrect password") {
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

  tracedTest("trust - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      port     = Port.Trust,
    ).use(_ => IO.unit)
  }

  // TODO: should this be an error?
  tracedTest("trust - successful login, ignored password") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "world",
      password = Some("ignored"),
      port     = Port.Trust,
    ).use(_ => IO.unit)
  }

  tracedTest("trust - non-existent database") {
    Session.single[IO](
      host     = "localhost",
      user     = "postgres",
      database = "bogus",
      port     = Port.Trust,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  tracedTest("trust - incorrect user") {
    Session.single[IO](
      host     = "localhost",
      user     = "bogus",
      database = "world",
      port     = Port.Trust,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28000"))
  }

  tracedTest("scram - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      port     = Port.Scram
    ).use(_ => IO.unit)
  }

  tracedTest("scram - non-existent database") {
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

  tracedTest("scram - missing password") {
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

  tracedTest("scram - incorrect user") {
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

  tracedTest("scram - incorrect password") {
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

  tracedTest("password - successful login") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      port     = Port.Password
    ).use(_ => IO.unit)
  }

  tracedTest("password - non-existent database") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "blah",
      password = Some("banana"),
      port     = Port.Password,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "3D000"))
  }

  tracedTest("password - missing password") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "blah",
      password = None,
      port     = Port.Password,
    ).use(_ => IO.unit)
     .assertFailsWith[SkunkException]
     .flatMap(e => assertEqual("message", e.message, "Password required."))
  }

  tracedTest("password - incorrect user") {
    Session.single[IO](
      host     = "localhost",
      user     = "frank",
      database = "world",
      password = Some("banana"),
      port     = Port.Password,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("password - incorrect password") {
    Session.single[IO](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("apple"),
      port     = Port.Password,
    ).use(_ => IO.unit)
     .assertFailsWith[StartupException]
     .flatMap(e => assertEqual("code", e.code, "28P01"))
  }

  tracedTest("invalid port") {
    Session.single[IO](
      host     = "localhost",
      user     = "bob",
      database = "nobody cares",
      port     = Port.Invalid
    ).use(_ => IO.unit).assertFailsWith[ConnectException]
  }

  tracedTest("invalid host") {
    Session.single[IO](
      host     = "blergh",
      user     = "bob",
      database = "nobody cares",
    ).use(_ => IO.unit).assertFailsWith[UnknownHostException]
  }
}
