// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import cats.implicits._
import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._
import cats.Contravariant
import fs2._
import skunk.exception.UnexpectedRowsException

case object CommandTest extends SkunkTest {

  case class City(id: Int, name: String, code: String, district: String, pop: Int)

  val city: Codec[City] =
    (int4 ~ varchar ~ bpchar(3) ~ varchar ~ int4).gimap[City]

  val Garin = City(5000, "Garin", "ARG", "Escobar", 11405)
  val Garin2 = City(5001, "Garin2", "ARG", "Escobar", 11405)
  val Garin3 = City(5002, "Garin3", "ARG", "Escobar", 11405)

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

  val insertCity2a: Command[City] =
    Contravariant[Command].contramap(
      sql"""
          INSERT INTO city
          VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
        """.command
    ) {
      case c => c.id ~ c.name ~ c.code ~ c.district ~ c.pop
    }

  val selectCity: Query[Int, City] =
    sql"""
          SELECT * FROM city
          WHERE id = $int4
        """.query(city)

  val updateCityPopulation: Command[Int ~ Int] =
    sql"""
         UPDATE city
         SET population = $int4
         WHERE id = $int4
       """.command

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

  val alterTable: Command[Void] =
    sql"""
      ALTER TABLE earth RENAME COLUMN id TO pk
      """.command

  val dropTable: Command[Void] =
    sql"""
      DROP TABLE earth
      """.command

  val createSchema: Command[Void] =
    sql"""
      CREATE SCHEMA IF NOT EXISTS public_0
      """.command

  val dropSchema: Command[Void] =
    sql"""
      DROP SCHEMA public_0
      """.command

  sessionTest("create, alter and drop table") { s =>
    for {
      c <- s.execute(createTable)
      _ <- assert("completion",  c == Completion.CreateTable)
      c <- s.execute(alterTable)
      _ <- assert("completion",  c == Completion.AlterTable)
      c <- s.execute(dropTable)
      _ <- assert("completion",  c == Completion.DropTable)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create and drop schema") { s =>
    for {
      c <- s.execute(createSchema)
      _ <- assert("completion",  c == Completion.CreateSchema)
      c <- s.execute(dropSchema)
      _ <- assert("completion",  c == Completion.DropSchema)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert, update and delete record") { s =>
    for {
      c <- s.prepare(insertCity).use(_.execute(Garin))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).use(_.unique(Garin.id))
      _ <- assert("read", c == Garin)
      p <- IO(Garin.pop + 1000)
      c <- s.prepare(updateCityPopulation).use(_.execute(p ~ Garin.id))
      _ <- assert("completion",  c == Completion.Update(1))
      c <- s.prepare(selectCity).use(_.unique(Garin.id))
      _ <- assert("read", c == Garin.copy(pop = p))
      _ <- s.prepare(deleteCity).use(_.execute(Garin.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command") { s =>
    for {
      c <- s.prepare(insertCity2).use(_.execute(Garin2))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).use(_.unique(Garin2.id))
      _ <- assert("read", c == Garin2)
      _ <- s.prepare(deleteCity).use(_.execute(Garin2.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command (via Contravariant instance") { s =>
    for {
      c <- s.prepare(insertCity2a).use(_.execute(Garin3))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).use(_.unique(Garin3.id))
      _ <- assert("read", c == Garin3)
      _ <- s.prepare(deleteCity).use(_.execute(Garin3.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("pipe") { s =>
    for {
      _ <- s.execute(sql"delete from city where name like 'Pipe%'".command)
      _ <- Stream(
            City(5100, "Pipe1", "ARG", "Escobar", 11405),
            City(5101, "Pipe2", "ARG", "Escobar", 11405),
            City(5102, "Pipe3", "ARG", "Escobar", 11405),
            City(5103, "Pipe4", "ARG", "Escobar", 11405),
          ).through(s.pipe(insertCity)).compile.drain
      n <- s.unique(sql"select count(*) from city where name like 'Pipe%'".query(int8))
      _ <- assertEqual("count", n, 4)
    } yield "ok"
  }

  // sessionTest("should be a query") { s =>
  //   s.execute(sql"select * from country".command).as("ok") // BUG! this is a protocol error
  // }

  sessionTest("should be a query") { s =>
    s.prepare(sql"select * from country".command)
      .use(_ => IO.unit)
      .assertFailsWith[UnexpectedRowsException]
      .as("ok")
  }

}
