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
    
  pooledTest("prepare cache should close evicted prepared statements at end of session", max = 1, parseCacheSize = 1) { p =>
    p.use { s =>
      s.execute(pgStatementsByName)("foo").void >>
      s.execute(pgStatementsByStatement)("bar").void >>
      s.execute(pgStatementsCountByStatement)("baz").void >>
      s.parseCache.value.values.map { values =>
        assertEquals(values.size, 2, "two prepared statements are cached and one has been evicted")
      } >>
      s.execute(pgStatementsCount).map { count =>
        assertEquals(count, List(3L), "cached and evicted prepared statements should still exist on server")   
      }
    } >>
    p.use { s =>
      // now verify the evicted prepared statement was closed when session returned to the pool
      s.execute(pgStatements).map { statements =>
        assertEquals(statements, List(
          "select true from pg_prepared_statements where statement = $1",
          "select count(*) from pg_prepared_statements where statement = $1"
        ))   
      } 
    } 
  }

  pooledTest("prepared statements via prepare shouldn't get evicted until they go out of scope", max = 1, parseCacheSize = 1) { p =>
    p.use { s =>
      // creates entry in cache
      s.prepare(pgStatementsByName)   
    }.flatMap { pq =>
      p.use { s =>
        pq.option("bar") >>
        // create two more prepared statements which will evicted our pq
        s.execute(pgStatementsByStatement)("foo") >> 
        s.execute(pgStatementsCountByStatement)("bar") >>
        // despite being evicted, close of the statement is deferred until session is recycled, allowing use of the prepared statement
        pq.option("bar")
      }   
    }
  }

  pooledTest("statements prepared via prepareR are not cached and are closed immediately", max = 1) { p =>
    p.use { s =>
      s.prepareR(pgStatementsByName).use { pq =>
        pq.option("bar").void >>
        s.parseCache.value.values.map(vs => assertEquals(vs.size, 0, "statement should not have been cached"))
      } >>
      s.execute(pgStatements).map { statements =>
        assertEquals(statements, Nil)
      }
    }
  }
}
