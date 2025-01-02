// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.numeric.int8
// import cats.syntax.all._
// import cats.effect.IO
import skunk.codec.text
import skunk.codec.boolean

class PrepareCacheTest extends SkunkTest {

  private val pgStatementsByName = sql"select true from pg_prepared_statements where name = ${text.text}".query(boolean.bool)
  private val pgStatementsCount = sql"select count(*) from pg_prepared_statements".query(int8)

  pooledTest("empty prepare cache should close prepared statements at end of session", max=1, parseCacheSize = 0) { p =>
    p.use { s =>
      s.execute(pgStatementsByName)("foo").void >>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(1L), "all prepared statements should be closed")   
      }
    } >> 
    p.use { s =>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(0L), "all prepared statements should be closed")   
      }
    } 
  }
}
