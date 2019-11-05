// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

case object CommandTest extends SkunkTest {

  case class City(id: Int, name: String, code: String, district: String, pop: Int)

  val city: Codec[City] =
    (int4 ~ varchar ~ bpchar(3) ~ varchar ~ int4).gimap[City]

  val Garin = City(5000, "Garin", "ARG", "Escobar", 11405)

  val insertCity: Command[City] =
    sql"""
         INSERT INTO city
         VALUES ($city)
       """.command

  // https://github.com/tpolecat/skunk/issues/83
  val insertCity2: Command[City] =
    sql"""
        INSERT INTO city
        VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
      """.command.contramap {
            case c => c.id ~ c.name ~ c.code ~ c.district ~ c.pop
          }

  val selectCity: Query[Int, City] =
    sql"""
          SELECT * FROM city
          WHERE id = $int4
        """.query(city)

  val deleteCity: Command[Int] =
    sql"""
         DELETE FROM city
         WHERE id = $int4
       """.command

  val createTable: Command[Void] =
    sql"""
      CREATE TABLE IF NOT EXISTS earth (
          id integer NOT NULL
      )
      """.command

  val dropTable: Command[Void] =
    sql"""
      DROP TABLE earth
      """.command

  sessionTest("create and drop table") { s =>
    for {
      c <- s.execute(createTable)
      _ <- assert("completion",  c == Completion.CreateTable)
      c <- s.execute(dropTable)
      _ <- assert("completion",  c == Completion.DropTable)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record") { s =>
    for {
      c <- s.prepare(insertCity).use(_.execute(Garin))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).use(_.unique(Garin.id))
      _ <- assert("read", c == Garin)
      _ <- s.prepare(deleteCity).use(_.execute(Garin.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command") { s =>
    for {
      c <- s.prepare(insertCity2).use(_.execute(Garin))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).use(_.unique(Garin.id))
      _ <- assert("read", c == Garin)
      _ <- s.prepare(deleteCity).use(_.execute(Garin.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

}
