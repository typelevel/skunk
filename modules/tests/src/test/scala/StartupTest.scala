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

  def session(user: String, password: Option[String]): Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = user,
      database = "world",
      password = password,
    )

  test("successful login") {
    session("jimmy", Some("banana")).use(_ => IO.unit)
  }

  test("missing password") {
    for {
      e <- session("jimmy", None).use(_ => IO.unit).assertFailsWith[SkunkException]
      _ <- assertEqual("message", e.message, "Password required.")
    } yield ()
  }

  test("incorrect user") {
    for {
      e <- session("frank", Some("apple")).use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "28P01")
    } yield ()
  }

  test("incorrect password") {
    for {
      e <- session("jimmy", Some("apple")).use(_ => IO.unit).assertFailsWith[StartupException]
      _ <- assertEqual("code", e.code, "28P01")
    } yield ()
  }

}
