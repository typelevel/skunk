// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package data

import skunk.data.Identifier
import skunk.implicits._
import cats.syntax.all._
import cats.effect.IO
import scala.annotation.nowarn

class IdentifierTest extends ffstest.FTest {

  @nowarn("cat=deprecation")
  private def legacyFromString(s: String): Either[String, Identifier] =
    Identifier.fromString(s)

  test("fromString - valid, folded to lower case") {
    legacyFromString("Identifier") match {
      case Left(err) => fail(err)
      case Right(id) => assertEqual("value", id.value, "identifier")
    }
  }

  test("fromString - invalid lexical") {
    legacyFromString("7_@*#&") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("fromString - too long") {
    legacyFromString("x" * 100) match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("fromString - empty") {
    legacyFromString("") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("fromString - reserved word") {
    legacyFromString("select") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("fromValue - valid, preserves case and dots") {
    Identifier.fromValue("q_my_queue.INSERT") match {
      case Left(err) => fail(err)
      case Right(id) =>
        for {
          _ <- assertEqual("value", id.value, "q_my_queue.INSERT")
          _ <- assertEqual("sql", id.sql, "\"q_my_queue.INSERT\"")
        } yield ()
    }
  }

  test("fromValue - keyword allowed") {
    Identifier.fromValue("SELECT") match {
      case Left(err) => fail(err)
      case Right(id) =>
        for {
          _ <- assertEqual("value", id.value, "SELECT")
          _ <- assertEqual("sql", id.sql, "\"SELECT\"")
        } yield ()
    }
  }

  test("fromValue - empty rejected") {
    Identifier.fromValue("") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("fromValue - null byte rejected") {
    Identifier.fromValue("foo" + '\u0000' + "bar") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("fromValue - too long in bytes") {
    // "é" is two bytes in UTF-8, so 32 of them is 64 bytes, one over the limit.
    Identifier.fromValue("é" * 32) match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("sql - unquoted when safe") {
    assertEqual("sql", (id"foo_bar": @nowarn("cat=deprecation")).sql, "foo_bar")
  }

  test("sql - quoted keyword") {
    Identifier.fromValue("select") match {
      case Left(err) => fail(err)
      case Right(id) => assertEqual("sql", id.sql, "\"select\"")
    }
  }

  test("sql - escapes embedded double quotes") {
    Identifier.fromValue("has\"quote") match {
      case Left(err) => fail(err)
      case Right(id) => assertEqual("sql", id.sql, "\"has\"\"quote\"")
    }
  }

  test("sql - mixed-case id is folded and renders unquoted") {
    // Regression: the (deprecated) `id"…"` interpolator folds to lower case so it round-trips through
    // Postgres unchanged, rather than being quoted (which would make it case-sensitive).
    val folded = (id"Foo": @nowarn("cat=deprecation"))
    for {
      _ <- assertEqual("value", folded.value, "foo")
      _ <- assertEqual("sql", folded.sql, "foo")
    } yield ()
  }

  test("ident - interpolator preserves case and renders quoted") {
    val quoted = ident"q_my_queue.INSERT"
    for {
      _ <- assertEqual("value", quoted.value, "q_my_queue.INSERT")
      _ <- assertEqual("sql", quoted.sql, "\"q_my_queue.INSERT\"")
    } yield ()
  }

  test("ident - interpolator leaves a safe lower-case name unquoted") {
    val plain = ident"foo_bar"
    for {
      _ <- assertEqual("value", plain.value, "foo_bar")
      _ <- assertEqual("sql", plain.sql, "foo_bar")
    } yield ()
  }

  test("toString returns the sql form") {
    assertEqual("toString", ident"My.Channel".toString, "\"My.Channel\"")
  }

  test("Eq compares identifier values") {
    val legacy = legacyFromString("foo").toOption.get
    val current = Identifier.fromValue("foo").toOption.get
    for {
      _ <- IO(assert(legacy === current))
      _ <- IO(assert(legacy == current))
    } yield ()
  }

}
