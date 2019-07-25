// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import skunk._
import skunk.implicits._
import skunk.codec.all._

object Statements {

  // def statement(region: String, indepyear: Short): Query0[(String, String, Int)] =
  //   sql"""
  //     SELECT name, code, population
  //     FROM   country
  //     WHERE  region    = $region
  //     AND    indepyear > $indepyear
  //   """.query[(String, String, Int)]

  val statement: Query[String ~ Short, String ~ String ~ Int] =
    sql"""
      SELECT name, code, population
      FROM   country
      WHERE  region    = $varchar
      AND    indepyear > $int2
    """.query(varchar ~ varchar ~ int4)

}
