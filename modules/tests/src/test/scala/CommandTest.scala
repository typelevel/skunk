// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

case object CommandTest extends SkunkTest {

  val insertCity: Command[Int ~ String ~ String ~ String ~ Long] =
    sql"""
         INSERT INTO city
         VALUES ($int4, $varchar, $varchar, $varchar, $int8)
       """.command

  val deleteCity: Command[Int] =
    sql"""
         DELETE FROM city
         WHERE id = $int4
       """.command

  sessionTest("insert and delete record") { s =>
    for {
      c <- s.prepare(insertCity).use { cmd =>
             cmd.execute(5000 ~ "Garin" ~ "ARG" ~ "Escobar" ~ 11405)
           }
      _ <- assert("completion",  c == Completion.Insert)
      _ <- s.prepare(deleteCity).use { cmd =>
             cmd.execute(5000)
           }
      _ <- s.assertHealthy
    } yield "ok"
  }

}
