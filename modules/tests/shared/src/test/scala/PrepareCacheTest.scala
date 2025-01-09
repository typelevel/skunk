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
  
  pooledTest("prepared statements via prepare shoudln't get evicted until they go out of scope", max=1, parseCacheSize = 1) { p =>
    p.use { s =>
      // creates entry in cache
      s.prepare(pgStatementsByName)   
    }.flatMap { pq =>
      p.use { s =>
        // this works
        pq.option("bar") >>
        // create two more prepared statements which will evicted our pq
        s.execute(pgStatementsByStatement)("foo") >> 
        s.execute(pgStatementsCountByStatement)("bar") >>
        // This blows up because cache is referring to prepared statement in postgres that doesn't exist anymore
        pq.option("bar")
      }   
    }
  }  
  
  pooledTest("prepare cache should close evicted prepared statements at end of session", max=1, parseCacheSize = 1) { p =>
    p.use { s =>
      s.execute(pgStatementsByName)("foo").void >>
      s.execute(pgStatementsByStatement)("bar").void >>
      s.execute(pgStatementsCountByStatement)("baz").void >>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(2L), "cached prepared statements should still exist but evicted ones should not")   
      }
    } >> 
    p.use { s =>
      // Because of the way semispace cache works the last *two* statements will be cached even though the max size is 1
      // With a max of N, the max cache size is really N*2 because it keeps gen0/gen1
      s.execute(pgStatements).map { statements =>
        assertEquals(statements, List(
          "select true from pg_prepared_statements where statement = $1",
          "select count(*) from pg_prepared_statements where statement = $1"
        ))   
      } 
    } 
  }
}
