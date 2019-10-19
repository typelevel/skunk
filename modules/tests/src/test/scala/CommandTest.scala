// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.codec.all._
import skunk.implicits._
import skunk.data.Completion

case object CommandTest extends SkunkTest {

  val insertCity: Command[Int ~ String ~ String ~ String ~ Long] =
    sql"""
         INSERT INTO city
         VALUES ($int4, $varchar, $varchar, $varchar, $int8)
       """.command

  sessionTest("insert values") { s =>
    for {
      c <- s.prepare(insertCity).use { cmd =>
             cmd.execute(4080 ~ "Garin" ~ "ARG" ~ "Escobar" ~ 11405)
           }
      _ <- assert("completion",  c == Completion.Insert)
      _ <- s.assertHealthy
    } yield "ok"
  }

}
