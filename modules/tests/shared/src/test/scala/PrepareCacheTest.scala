// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.numeric.int8
import skunk.codec.text
import skunk.codec.boolean

class PrepareCacheTest extends SkunkTest {

  private val pgStatementsByName = sql"select true from pg_prepared_statements where name = ${text.text}".query(boolean.bool)
  private val pgStatementsByStatement = sql"select true from pg_prepared_statements where statement = ${text.text}".query(boolean.bool)
  private val pgStatementsCountByStatement = sql"select count(*) from pg_prepared_statements where statement = ${text.text}".query(int8)
  private val pgStatementsCount = sql"select count(*) from pg_prepared_statements".query(int8)
  private val pgStatements = sql"select statement from pg_prepared_statements order by prepare_time".query(text.text)

  pooledTest("empty prepare cache should close prepared statements at end of session", max=1, parseCacheSize = 0) { p =>
    p.use { s =>
      s.execute(pgStatementsByName)("foo").void >>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(1L), "cached and evicted prepared statements should still exist")   
      }
    } >> 
    p.use { s =>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(0L), "all prepared statements should be closed since we have size 0")   
      }
    } 
  }
  
  pooledTest("prepare cache should close evicted prepared statements at end of session", max=1, parseCacheSize = 1) { p =>
    p.use { s =>
      s.execute(pgStatementsByName)("foo").void >>
      s.execute(pgStatementsByStatement)("bar").void >>
      s.execute(pgStatementsCountByStatement)("baz").void >>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(3L), "cached and evicted prepared statements should still exist")   
      }
    } >> 
    p.use { s =>
      // Because of the way semispace cache works the last *two* statements will be caused even though the size is 1
      // With a max of N the max size of the cache is really N*2 because it keeps gen0/gen1
      s.execute(pgStatements).map { statements =>
        assertEquals(statements, List(
          "select true from pg_prepared_statements where statement = $1",
          "select count(*) from pg_prepared_statements where statement = $1"
        ))   
      } 
    } 
  }
}
