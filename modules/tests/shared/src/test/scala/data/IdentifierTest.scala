// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package data

import skunk.data.Identifier
import skunk.implicits._
import cats.syntax.all._
import cats.effect.IO

class IdentifierTest extends ffstest.FTest {

  test("valid") {
    Identifier.fromString("Identifier") match {
      case Left(err) => fail(err)
      case Right(id) => assertEqual("value", id.value, "Identifier")
    }
  }

  test("invalid - lexical") {
    Identifier.fromString("7_@*#&") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("invalid - too long") {
    Identifier.fromString("x" * 100) match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("invalid - too short") {
    Identifier.fromString("") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("invalid - reserved word") {
    Identifier.fromString("select") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("toString") {
    assertEqual("value", id"foo".toString, "foo")
  }

}

