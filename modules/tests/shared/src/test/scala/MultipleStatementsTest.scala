// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import skunk.exception.SkunkException
import cats.effect.IO
import skunk.exception.PostgresErrorException

class MultipleStatementsTest extends SkunkTest {

  val statements: List[(Query[Void,Int], Command[Void])] =
    List("select 1","commit","copy country from stdin","copy country to stdout") // one per protocol
      .permutations
      .toList
      .map { ss => ss.intercalate(";") }
      .map { s => (sql"#$s".query(int4), sql"#$s".command) }

  statements.foreach { case (q, c) =>
    sessionTest(s"query: ${q.sql}") { s =>  s.execute(q).assertFailsWith[SkunkException] *> s.assertHealthy }
    sessionTest(s"command: ${c.sql}") { s =>  s.execute(c).assertFailsWith[SkunkException] *> s.assertHealthy }
  }

  sessionTest("extended query (postgres raises an error here)") { s =>
    s.prepare(sql"select 1;commit".query(int4))
      .use(_ => IO.unit)
      .assertFailsWith[PostgresErrorException] *> s.assertHealthy
  }

  sessionTest("extended command (postgres raises an error here)") { s =>
    s.prepare(sql"select 1;commit".command)
      .use(_ => IO.unit)
      .assertFailsWith[PostgresErrorException] *> s.assertHealthy
  }

}