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

  test("quoted - valid with dots") {
    Identifier.fromStringQuoted("q_my_queue.INSERT") match {
      case Left(err) => fail(err)
      case Right(id) =>
        for {
          _ <- assertEqual("value", id.value, "q_my_queue.INSERT")
          _ <- assertEqual("asSql", id.asSql, "\"q_my_queue.INSERT\"")
          _ <- assertEqual("quoted", id.quoted, true)
        } yield ()
    }
  }

  test("quoted - escapes embedded double quotes") {
    Identifier.fromStringQuoted("with\"quote") match {
      case Left(err) => fail(err)
      case Right(id) => assertEqual("asSql", id.asSql, "\"with\"\"quote\"")
    }
  }

  test("quoted - empty rejected") {
    Identifier.fromStringQuoted("") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("quoted - null byte rejected") {
    val nullByte = '\u0000'
    Identifier.fromStringQuoted(s"a${nullByte}b") match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("quoted - too long in bytes") {
    Identifier.fromStringQuoted("é" * 32) match {
      case Left(err) => err.pure[IO]
      case Right(value) => fail(s"expected error, got $value")
    }
  }

  test("quoted - keywords allowed") {
    Identifier.fromStringQuoted("SELECT") match {
      case Left(err) => fail(err)
      case Right(id) =>
        for {
          _ <- assertEqual("value", id.value, "SELECT")
          _ <- assertEqual("asSql", id.asSql, "\"SELECT\"")
        } yield ()
    }
  }

  test("unquoted - asSql equals value") {
    assertEqual("asSql", id"foo".asSql, "foo")
  }

  test("Eq distinguishes quoted from unquoted with same value") {
    val unquoted = Identifier.fromString("foo").toOption.get
    val quoted = Identifier.fromStringQuoted("foo").toOption.get
    for {
      _ <- IO(assert(unquoted =!= quoted))
      _ <- IO(assert(unquoted != quoted))
    } yield ()
  }

}

