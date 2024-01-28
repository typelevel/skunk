// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import skunk.exception.{SkunkException, CopyNotSupportedException, PostgresErrorException}
import cats.effect.IO

class MultipleStatementsTest extends SkunkTest {

  val statements: List[(Query[Void,Int], Command[Void], Statement[Void])] =
    List("select 1","commit","copy country from stdin","copy country to stdout") // one per protocol
      .permutations
      .toList
      .map { ss => ss.intercalate(";") }
      .map { s => (sql"#$s".query(int4), sql"#$s".command, sql"#$s".command) }

  statements.foreach { case (q, c, any) =>
    sessionTest(s"query: ${q.sql}") { s =>  s.execute(q).assertFailsWith[SkunkException] *> s.assertHealthy }
    sessionTest(s"command: ${c.sql}") { s =>  s.execute(c).assertFailsWith[SkunkException] *> s.assertHealthy }
    sessionTest(s"any discarded: ${any.sql}") { s =>  s.executeDiscard(any).assertFailsWith[CopyNotSupportedException] *> s.assertHealthy }
  }

  // statements with no errors
  List(
    """CREATE FUNCTION do_something() RETURNS integer AS $$ BEGIN RETURN 1; END; $$ LANGUAGE plpgsql;
       SELECT do_something();
       DROP FUNCTION do_something
       """,
    """ALTER TABLE city ADD COLUMN idd SERIAL;
       SELECT setval('city_idd_seq', max(id)) FROM city;
       ALTER TABLE city DROP COLUMN idd""",
    "/* empty */")
    .permutations
    .toList
    .map(s => sql"#${s.intercalate(";")}".command)
    .foreach { stmt =>
      sessionTest(s"discarded no errors: ${stmt.sql}") { s =>
        s.executeDiscard(stmt) *> s.assertHealthy
      }
    }

  // statements with different errors  
  {
    val copy = "copy country from stdin"
    val conflict = "create table country()"

    Vector("select 1","commit",conflict,copy)
    .permutations
    .toVector
    .foreach { statements =>
      val stmt = sql"#${statements.intercalate(";")}".command

      if (statements.indexOf(conflict) < statements.indexOf(copy))
        sessionTest(s"discarded with postgres error: ${stmt.sql}")(s => 
          s.executeDiscard(stmt).assertFailsWith[PostgresErrorException] *> s.assertHealthy
        )
      else
        sessionTest(s"discarded with unsupported error: ${stmt.sql}")(s =>
          s.executeDiscard(stmt).assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
        )
    }
  }

  sessionTest("extended query (postgres raises an error here)") { s =>
    s.prepare(sql"select 1;commit".query(int4))
      .flatMap(_ => IO.unit)
      .assertFailsWith[PostgresErrorException] *> s.assertHealthy
  }

  sessionTest("extended command (postgres raises an error here)") { s =>
    s.prepare(sql"select 1;commit".command)
      .flatMap(_ => IO.unit)
      .assertFailsWith[PostgresErrorException] *> s.assertHealthy
  }

}