// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._
import cats.Contravariant
import fs2._
import skunk.exception.UnexpectedRowsException

class CommandTest extends SkunkTest {

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

  val createIndex: Command[Void] =
    sql"""
      CREATE INDEX IF NOT EXISTS id_index ON earth (
        id
      )
      """.command

  val dropIndex: Command[Void] =
    sql"""
      DROP INDEX id_index
      """.command

  val createView: Command[Void] =
    sql"""
      CREATE OR REPLACE VIEW city_view AS
      SELECT * FROM city
      """.command

  val dropView: Command[Void] =
    sql"""
      DROP VIEW city_view
      """.command

  val createProcedure: Command[Void] =
    sql"""
      CREATE OR REPLACE PROCEDURE proc(n integer)
      LANGUAGE plpgsql
      AS $$$$
      BEGIN
        RAISE DEBUG 'proc called';
      END;
      $$$$;
      """.command

  val dropProcedure: Command[Void] =
    sql"""
      DROP PROCEDURE proc
      """.command

  val callProcedure: Command[Void] =
    sql"""
      CALL proc(123)
      """.command

  val doCommand : Command[Void] =
    sql"""
      DO $$$$ begin
        CREATE DOMAIN population as int4 check (value >= 0);
        CREATE DOMAIN population as int4 check (value >= 0);
      EXCEPTION
      WHEN duplicate_object THEN null;
      END $$$$;
    """.command

  val createDomain : Command[Void] =
    sql"""
        CREATE DOMAIN population as int4 check (value >= 0)
       """.command

  val dropDomain : Command[Void] =
    sql"""
        DROP DOMAIN IF EXISTS population
       """.command

  val createSequence: Command[Void] =
    sql"""
        CREATE SEQUENCE IF NOT EXISTS counter_seq
       """.command

  val alterSequence: Command[Void] =
    sql"""
        ALTER SEQUENCE IF EXISTS counter_seq INCREMENT BY 2
       """.command

  val dropSequence: Command[Void] =
    sql"""
        DROP SEQUENCE IF EXISTS counter_seq
       """.command

  val createDatabase: Command[Void] =
    sql"""
        CREATE DATABASE skunk_database
       """.command

  val dropDatabase: Command[Void] =
    sql"""
        DROP DATABASE IF EXISTS skunk_database
       """.command

  val createRole: Command[Void] =
    sql"""
        CREATE ROLE skunk_role
       """.command

  val dropRole: Command[Void] =
    sql"""
        DROP ROLE skunk_role
       """.command

  val createMaterializedView: Command[Void] =
    sql"""
        CREATE MATERIALIZED VIEW IF NOT EXISTS my_foo_mv
        AS
        SELECT now()
       """.command

  val createUniqueIndexForMaterializedView: Command[Void] =
    sql"""
        CREATE UNIQUE INDEX IF NOT exists my_foo_mv_unique ON my_foo_mv(now)
        """.command

  val refreshMaterializedView: Command[Void] =
    sql"""
        REFRESH MATERIALIZED VIEW my_foo_mv
       """.command

 val refreshMaterializedViewConcurrently: Command[Void] =
    sql"""
        REFRESH MATERIALIZED VIEW CONCURRENTLY my_foo_mv
       """.command

  sessionTest("create table, create index, drop index, alter table and drop table") { s =>
    for {
      c <- s.execute(createTable)
      _ <- assert("completion",  c == Completion.CreateTable)
      c <- s.execute(createIndex)
      _ <- assert("completion",  c == Completion.CreateIndex)
      c <- s.execute(dropIndex)
      _ <- assert("completion",  c == Completion.DropIndex)
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

  sessionTest("create view, drop view"){ s=>
    for{
      c <- s.execute(createView)
      _ <- assert("completion", c == Completion.CreateView)
      c <- s.execute(dropView)
      _ <- assert("completion", c == Completion.DropView)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create, call and drop procedure"){ s=>
    for{
      c <- s.execute(createProcedure)
      _ <- assert("completion", c == Completion.CreateProcedure)
      c <- s.execute(callProcedure)
      _ <- assert("completion", c == Completion.Call)
      c <- s.execute(dropProcedure)
      _ <- assert("completion", c == Completion.DropProcedure)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create domain, drop domain"){ s=>
    for{
      c <- s.execute(dropDomain)
      _ <- assert("completion", c == Completion.DropDomain)
      c <- s.execute(createDomain)
      _ <- assert("completion", c == Completion.CreateDomain)
      c <- s.execute(dropDomain)
      _ <- assert("completion", c == Completion.DropDomain)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create sequence, alter sequence,  drop sequence"){ s=>
    for{
      c <- s.execute(createSequence)
      _ <- assert("completion", c == Completion.CreateSequence)
      c <- s.execute(alterSequence)
      _ <- assert("completion", c == Completion.AlterSequence)
      c <- s.execute(dropSequence)
      _ <- assert("completion", c == Completion.DropSequence)
    } yield "ok"
  }

  sessionTest("create database, drop database"){ s=>
    for{
      c <- s.execute(createDatabase)
      _ <- assert("completion", c == Completion.CreateDatabase)
      c <- s.execute(dropDatabase)
      _ <- assert("completion", c == Completion.DropDatabase)
    } yield "ok"
  }

  sessionTest("create role, drop role") { s =>
    for {
      c <- s.execute(createRole)
      _ <- assert("completion", c == Completion.CreateRole)
      c <- s.execute(dropRole)
      _ <- assert("completion", c == Completion.DropRole)
    } yield "ok"
  }

  sessionTest("refresh materialized view, refresh materialized view concurrently") { s =>
    for{
      c <- s.execute(createMaterializedView)
      _ <- assert("completion " + c, c == Completion.CreateMaterializedView)
      c <- s.execute(refreshMaterializedView)
      _ <- assert("completion", c == Completion.RefreshMaterializedView)
      c <- s.execute(createUniqueIndexForMaterializedView)
      _ <- assert("completion",  c == Completion.CreateIndex)
      c <- s.execute(refreshMaterializedViewConcurrently)
      _ <- assert("completion", c == Completion.RefreshMaterializedView)
    } yield "ok"
  }

  sessionTest("do command") { s =>
    for{
      c <- s.execute(doCommand)
      _ <- assert("completion", c == Completion.Do)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert, update and delete record") { s =>
    for {
      c <- s.execute(insertCity, Garin)
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.unique(selectCity, Garin.id)
      _ <- assert("read", c == Garin)
      p <- IO(Garin.pop + 1000)
      c <- s.execute(updateCityPopulation, p ~ Garin.id)
      _ <- assert("completion",  c == Completion.Update(1))
      c <- s.unique(selectCity, Garin.id)
      _ <- assert("read", c == Garin.copy(pop = p))
      _ <- s.execute(deleteCity, Garin.id)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command") { s =>
    for {
      c <- s.prepare(insertCity2).flatMap(_.execute(Garin2))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).flatMap(_.unique(Garin2.id))
      _ <- assert("read", c == Garin2)
      _ <- s.prepare(deleteCity).flatMap(_.execute(Garin2.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command (via Contravariant instance") { s =>
    for {
      c <- s.prepare(insertCity2a).flatMap(_.execute(Garin3))
      _ <- assert("completion",  c == Completion.Insert(1))
      c <- s.prepare(selectCity).flatMap(_.unique(Garin3.id))
      _ <- assert("read", c == Garin3)
      _ <- s.prepare(deleteCity).flatMap(_.execute(Garin3.id))
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
      _ <- assertEqual("count", n, 4L)
    } yield "ok"
  }

  // sessionTest("should be a query") { s =>
  //   s.execute(sql"select * from country".command).as("ok") // BUG! this is a protocol error
  // }

  sessionTest("should be a query") { s =>
    s.prepare(sql"select * from country".command)
      .flatMap(_ => IO.unit)
      .assertFailsWith[UnexpectedRowsException]
      .as("ok")
  }

}
