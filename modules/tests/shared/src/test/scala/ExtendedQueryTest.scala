// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import fs2.Stream
import skunk._
import skunk.codec.all._
import skunk.implicits._

class ExtendedQueryTest extends SkunkTest {

  sessionTest("parameterized simple") { s =>
    val query =
      sql"""
      SELECT name, region FROM country
      WHERE continent = $varchar
        AND population > $int4
    """.query(varchar *: varchar)

    val countryStream = for {
      preparedQuery <- Stream.eval(s.prepare(query))
      country <- preparedQuery.stream("Europe" *: 10_000_000 *: EmptyTuple, chunkSize = 5)
    } yield country

    countryStream.compile.toList.map(_ => "ok")
  }

  sessionTest("parameterized w/ list (legacy twiddle)") { s =>
    import skunk.feature.legacyCommandSyntax
    val continents = List("Europe", "Asia")
    val query =
      sql"""
      SELECT name, region FROM country
      WHERE continent IN (${varchar.list(continents)})
        AND population > $int4
    """.query(varchar ~ varchar)

    val countryStream = for {
      preparedQuery <- Stream.eval(s.prepare(query))
      country <- preparedQuery.stream((continents, 10_000_000), chunkSize = 5)
    } yield country

    countryStream.compile.toList.map(_ => "ok")
  }

  // Uses import skunk._ for *: and EmptyTuple polyfills; can replace with :: and HNil if preferred
  sessionTest("parameterized w/ list") { s =>
    val continents = List("Europe", "Asia")
    val query = sql"""
      SELECT name, region FROM country
      WHERE continent IN (${varchar.list(continents)})
        AND population > $int4
    """.query(varchar *: varchar)

    val countryStream = for {
      preparedQuery <- Stream.eval(s.prepare(query))
      country <- preparedQuery.stream(new *:(continents, 10_000_000 *: EmptyTuple), chunkSize = 5)
    } yield country

    countryStream.compile.toList.map(_ => "ok")
  }

}
