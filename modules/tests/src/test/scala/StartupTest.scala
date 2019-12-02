// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import cats.implicits._
import skunk._
import natchez.Trace.Implicits.noop
import skunk.exception.SkunkException
import skunk.exception.StartupException

case object StartupTest extends ffstest.FTest {

  // Different ports for different authentication schemes.
  object Port {
    val MD5   = 5432
    val Trust = 5433
  }

  def session(user: String, password: Option[String], port: Int, database: String = "world"): Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = user,
      database = database,
      password = password,
      port     = port,
    )

  test("md5 - successful login") {
    session("jimmy", Some("banana"), Port.MD5).use(_ => IO.unit)
  }

  test("md5 - non-existent database") {
    for {
      e <- session("jimmy", Some("banana"), Port.MD5, database = "blah").use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "3D000")
    } yield ()
  }

  test("md5 - missing password") {
    for {
      e <- session("jimmy", None, Port.MD5).use(_ => IO.unit).assertFailsWith[SkunkException]
      _ <- assertEqual("message", e.message, "Password required.")
    } yield ()
  }

  test("md5 - incorrect user") {
    for {
      e <- session("frank", Some("apple"), Port.MD5).use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "28P01")
    } yield ()
  }

  test("md5 - incorrect password") {
    for {
      e <- session("jimmy", Some("apple"), Port.MD5).use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "28P01")
    } yield ()
  }

  test("trust - successful login") {
    session("postgres", None, Port.Trust).use(_ => IO.unit)
  }

  // TODO: should this be an error?
  test("trust - successful login, ignored password") {
    session("postgres", Some("ignored"), Port.Trust).use(_ => IO.unit)
  }

  test("trust - non-existent database") {
    for {
      e <- session("postgres", None, Port.Trust, database = "blah").use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "3D000")
    } yield ()
  }

  test("trust - incorrect user") {
    for {
      e <- session("frank", Some("apple"), Port.Trust).use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "28000")
    } yield ()
  }

}
