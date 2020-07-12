// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import skunk.exception.SkunkException

object MultipleStatementsTest extends SkunkTest {

  val statements: List[(Query[Void,Int], Command[Void])] =
    List("select 1","commit","copy country from stdin")
      .permutations
      .toList
      .map { ss => ss.intercalate(";") }
      .map { s => (sql"#$s".query(int4), sql"#$s".command) }

  statements.foreach { case (q, c) =>
    sessionTest(s"query: ${q.sql}") { s =>  s.execute(q).assertFailsWith[SkunkException] *> s.assertHealthy }
    sessionTest(s"command: ${c.sql}") { s =>  s.execute(c).assertFailsWith[SkunkException] *> s.assertHealthy }
  }

}