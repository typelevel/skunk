// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue
import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import skunk.exception.PostgresErrorException

// https://github.com/tpolecat/skunk/issues/181
class Test181 extends SkunkTest {

  def func(level: String): Command[Void] =
    sql"""
      CREATE OR REPLACE FUNCTION test181_#$level() RETURNS real AS $$$$
      BEGIN
          RAISE #$level 'This message contains non-ASCII characters: ü ø 😀 ש';
          RETURN 4.2;
      END;
      $$$$ LANGUAGE plpgsql;
    """.command

  sessionTest(s"issue/181 (EXCEPTION)") { s =>
    for {
      _ <- s.execute(func("EXCEPTION"))
      _ <- s.unique(sql"select test181_EXCEPTION()".query(float4)).assertFailsWith[PostgresErrorException]
      _ <- s.assertHealthy
    } yield ("ok")
  }

  sessionTest(s"issue/181 (WARNING)") { s =>
    for {
      _ <- s.execute(func("WARNING"))
      a <- s.unique(sql"select test181_WARNING()".query(float4))
      _ <- assertEqual("4.2", a, 4.2f)
    } yield ("ok")
  }

    sessionTest(s"issue/181 (NOTICE)") { s =>
    for {
      _ <- s.execute(func("NOTICE"))
      a <- s.unique(sql"select test181_NOTICE()".query(float4))
      _ <- assertEqual("4.2", a, 4.2f)
    } yield ("ok")
  }

}