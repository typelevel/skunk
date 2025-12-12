// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import skunk.*
import skunk.codec.all.*
import skunk.data.Completion
import skunk.implicits.*
import cats.Contravariant
import cats.kernel.Eq
import fs2.*
import skunk.exception.UnexpectedRowsException

class CommandTest extends SkunkTest {

  case class City(id: Int, name: String, code: String, district: String, pop: Int)

  val city: Codec[City] =
    (int4 *: varchar *: bpchar(3) *: varchar *: int4).to[City]

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
            c => (c.id, c.name, c.code, c.district, c.pop)
          }

  {
    import skunk.feature.legacyCommandSyntax
    @annotation.nowarn
    val insertCity2Legacy: Command[City] =
      sql"""
          INSERT INTO city
          VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
        """.command.contramap {
              c => c.id ~ c.name ~ c.code ~ c.district ~ c.pop
            }
  }

  val insertCity2a: Command[City] =
    Contravariant[Command].contramap(
      sql"""
          INSERT INTO city
          VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
        """.command
    ) {
      c => (c.id, c.name, c.code, c.district, c.pop)
    }

  {
    import skunk.feature.legacyCommandSyntax
    @annotation.nowarn
    val insertCity2b: Command[City] =
      Contravariant[Command].contramap(
        sql"""
            INSERT INTO city
            VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
          """.command
      ) {
        c => c.id ~ c.name ~ c.code ~ c.district ~ c.pop
      }
  }

  val selectCity: Query[Int, City] =
    sql"""
          SELECT * FROM city
          WHERE id = $int4
        """.query(city)

  val updateCityPopulation: Command[Int *: Int *: EmptyTuple] =
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

  val mergeCity: Command[Int] =
    sql"""
         MERGE INTO city
         USING (VALUES ($int4)) t(city_id) ON t.city_id = city.id
         WHEN MATCHED THEN DELETE
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

  val createType: Command[Void] =
    sql"""
      CREATE TYPE season AS ENUM ('winter', 'spring', 'summer')
      """.command

  val alterType: Command[Void] =
    sql"""
      ALTER TYPE season ADD VALUE 'autumn'
      """.command
  
  val dropType: Command[Void] =
    sql"""
      DROP TYPE season
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

  val alterIndex: Command[Void] =
    sql"""
      ALTER INDEX IF EXISTS id_index RENAME TO pk_index
      """.command

  val dropIndex: Command[Void] =
    sql"""
      DROP INDEX pk_index
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

  val alterRole: Command[Void] =
    sql"ALTER ROLE skunk_role WITH PASSWORD '123'".command

  val dropRole: Command[Void] =
    sql"""
        DROP ROLE skunk_role
       """.command

  val createExtension: Command[Void] =
    sql"""
        CREATE EXTENSION IF NOT EXISTS "uuid-ossp"
       """.command

  val dropExtension: Command[Void] =
    sql"""
        DROP EXTENSION "uuid-ossp"
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

  val dropMaterializedView: Command[Void] =
    sql"""
        DROP MATERIALIZED VIEW my_foo_mv
       """.command

  val createFunction: Command[Void] =
    sql"""
        CREATE OR REPLACE FUNCTION my_trigger_func() RETURNS TRIGGER
            LANGUAGE PLPGSQL
        AS
        'BEGIN
          RAISE NOTICE ''Triggered'';
          RETURN NEW;
        END;'
    """.command

  val alterFunction: Command[Void] =
    sql"""
        ALTER FUNCTION my_trigger_func() RESET search_path
    """.command

  val dropFunction: Command[Void] =
    sql"DROP FUNCTION my_trigger_func;".command

  val createTrigger: Command[Void] =
    sql"""
        CREATE TRIGGER my_city_trigger
        AFTER INSERT ON city
        FOR EACH ROW EXECUTE FUNCTION my_trigger_func();
       """.command

  val alterTrigger: Command[Void] =
    sql"""
        ALTER TRIGGER my_city_trigger ON city
        RENAME TO my_city_trigger_renamed;
       """.command

  val dropTrigger: Command[Void] =
    sql"""
        DROP TRIGGER my_city_trigger_renamed
        ON city;
       """.command

  val grant: Command[Void] =
    sql"""
        GRANT ALL PRIVILEGES
        ON ALL TABLES IN SCHEMA public
        TO skunk_role
       """.command

  val revoke: Command[Void] =
    sql"""
        REVOKE ALL PRIVILEGES
        ON ALL TABLES IN SCHEMA public
        FROM skunk_role
       """.command

  val addComment : Command[Void] =
    sql"COMMENT ON TABLE city IS 'A city'".command
  
  val removeComment : Command[Void] =
    sql"COMMENT ON TABLE city IS NULL".command

  val createPolicy: Command[Void] =
    sql"""
      CREATE POLICY my_policy ON city 
      TO CURRENT_USER
      WITH CHECK (FALSE)
       """.command

  val alterPolicy: Command[Void] =
    sql"""
      ALTER POLICY my_policy 
      ON city TO CURRENT_USER 
      WITH CHECK (TRUE)
       """.command
  
  val dropPolicy: Command[Void] =
    sql"DROP POLICY my_policy ON city".command

  val analyze: Command[Void] =
    sql"ANALYZE city".command
  
  val analyzeVerbose: Command[Void] =
    sql"ANALYZE VERBOSE city".command

  private implicit val eq: Eq[Completion] = Eq.fromUniversalEquals

  sessionTest("create table, create index, alter table, alter index, drop index and drop table") { s =>
    for {
      c <- s.execute(createTable)
      _ <- assertEqual("completion", c, Completion.CreateTable)
      c <- s.execute(createIndex)
      _ <- assertEqual("completion", c, Completion.CreateIndex)
      c <- s.execute(alterTable)
      _ <- assertEqual("completion", c, Completion.AlterTable)
      c <- s.execute(alterIndex)
      _ <- assertEqual("completion", c, Completion.AlterIndex)
      c <- s.execute(dropIndex)
      _ <- assertEqual("completion", c, Completion.DropIndex)
      c <- s.execute(dropTable)
      _ <- assertEqual("completion", c, Completion.DropTable)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create and drop trigger") { s =>
    for {
      c <- s.execute(createFunction)
      _ <- assertEqual("completion", c, Completion.CreateFunction)
      c <- s.execute(createTrigger)
      _ <- assertEqual("completion", c, Completion.CreateTrigger)
      c <- s.execute(alterTrigger)
      _ <- assertEqual("completion", c, Completion.AlterTrigger)
      c <- s.execute(dropTrigger)
      _ <- assertEqual("completion", c, Completion.DropTrigger)
      c <- s.execute(alterFunction)
      _ <- assertEqual("completion", c, Completion.AlterFunction)
      c <- s.execute(dropFunction)
      _ <- assertEqual("completion", c, Completion.DropFunction)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create and drop schema") { s =>
    for {
      c <- s.execute(createSchema)
      _ <- assertEqual("completion", c, Completion.CreateSchema)
      c <- s.execute(dropSchema)
      _ <- assertEqual("completion", c, Completion.DropSchema)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create, alter and drop type") { s =>
    for {
      c <- s.execute(createType)
      _ <- assertEqual("completion", c, Completion.CreateType)
      c <- s.execute(alterType)
      _ <- assertEqual("completion", c, Completion.AlterType)
      c <- s.execute(dropType)
      _ <- assertEqual("completion", c, Completion.DropType)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create, alter and drop policy") { s =>
    for {
      c <- s.execute(createPolicy)
      _ <- assertEqual("completion", c, Completion.CreatePolicy)
      c <- s.execute(alterPolicy)
      _ <- assertEqual("completion", c, Completion.AlterPolicy)
      c <- s.execute(dropPolicy)
      _ <- assertEqual("completion", c, Completion.DropPolicy)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create view, drop view"){ s=>
    for{
      c <- s.execute(createView)
      _ <- assertEqual("completion", c, Completion.CreateView)
      c <- s.execute(dropView)
      _ <- assertEqual("completion", c, Completion.DropView)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("refresh materialized view, refresh materialized view concurrently") { s =>
    for {
      c <- s.execute(createMaterializedView)
      _ <- assertEqual("completion", c, Completion.Select(1))
      c <- s.execute(createMaterializedView)
      _ <- assertEqual("completion", c, Completion.CreateMaterializedView)
      c <- s.execute(refreshMaterializedView)
      _ <- assertEqual("completion", c, Completion.RefreshMaterializedView)
      c <- s.execute(createUniqueIndexForMaterializedView)
      _ <- assertEqual("completion", c, Completion.CreateIndex)
      c <- s.execute(refreshMaterializedViewConcurrently)
      _ <- assertEqual("completion", c, Completion.RefreshMaterializedView)
      c <- s.execute(dropMaterializedView)
      _ <- assertEqual("completion", c, Completion.DropMaterializedView)
    } yield "ok"
  }

  sessionTest("create, call and drop procedure"){ s=>
    for{
      c <- s.execute(createProcedure)
      _ <- assertEqual("completion", c, Completion.CreateProcedure)
      c <- s.execute(callProcedure)
      _ <- assertEqual("completion", c, Completion.Call)
      c <- s.execute(dropProcedure)
      _ <- assertEqual("completion", c, Completion.DropProcedure)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create domain, drop domain"){ s=>
    for{
      c <- s.execute(dropDomain)
      _ <- assertEqual("completion", c, Completion.DropDomain)
      c <- s.execute(createDomain)
      _ <- assertEqual("completion", c, Completion.CreateDomain)
      c <- s.execute(dropDomain)
      _ <- assertEqual("completion", c, Completion.DropDomain)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("create sequence, alter sequence,  drop sequence"){ s=>
    for{
      c <- s.execute(createSequence)
      _ <- assertEqual("completion", c, Completion.CreateSequence)
      c <- s.execute(alterSequence)
      _ <- assertEqual("completion", c, Completion.AlterSequence)
      c <- s.execute(dropSequence)
      _ <- assertEqual("completion", c, Completion.DropSequence)
    } yield "ok"
  }

  sessionTest("create database, drop database"){ s=>
    for{
      c <- s.execute(createDatabase)
      _ <- assertEqual("completion", c, Completion.CreateDatabase)
      c <- s.execute(dropDatabase)
      _ <- assertEqual("completion", c, Completion.DropDatabase)
    } yield "ok"
  }

  sessionTest("create role, alter role, drop role") { s =>
    for {
      c <- s.execute(createRole)
      _ <- assertEqual("completion", c, Completion.CreateRole)
      c <- s.execute(alterRole)
      _ <- assertEqual("completion", c, Completion.AlterRole)
      c <- s.execute(dropRole)
      _ <- assertEqual("completion", c, Completion.DropRole)
    } yield "ok"
  }

  sessionTest("create extension, drop extension") { s =>
    for {
      c <- s.execute(createExtension)
      _ <- assertEqual("completion", c, Completion.CreateExtension)
      c <- s.execute(dropExtension)
      _ <- assertEqual("completion", c, Completion.DropExtension)
    } yield "ok"
  }

  sessionTest("do command") { s =>
    for{
      c <- s.execute(doCommand)
      _ <- assertEqual("completion", c, Completion.Do)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("add comment, remove comment") { s =>
    for{
      c <- s.execute(addComment)
      _ <- assertEqual("completion", c, Completion.Comment)
      c <- s.execute(removeComment)
      _ <- assertEqual("completion", c, Completion.Comment)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("analyze") { s =>
    for{
      c <- s.execute(analyze)
      _ <- assertEqual("completion", c, Completion.Analyze)
      v <- s.execute(analyzeVerbose)
      _ <- assert("completion", v == Completion.Analyze)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("set constraints") { s =>
    s.transaction.use { _ =>
      for {
        c <- s.execute(sql"set constraints all deferred".command)
        _ <- assertEqual("completion", c, Completion.SetConstraints)
      } yield "ok"
    } >> s.assertHealthy
  }

  sessionTest("insert, update and delete record") { s =>
    for {
      c <- s.execute(insertCity)(Garin)
      _ <- assertEqual("completion", c, Completion.Insert(1))
      c <- s.unique(selectCity)(Garin.id)
      _ <- assert("read", c == Garin)
      p <- IO(Garin.pop + 1000)
      c <- s.execute(updateCityPopulation)((p, Garin.id))
      _ <- assertEqual("completion", c, Completion.Update(1))
      c <- s.unique(selectCity)(Garin.id)
      _ <- assert("read", c == Garin.copy(pop = p))
      _ <- s.execute(deleteCity)(Garin.id)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command") { s =>
    for {
      c <- s.prepare(insertCity2).flatMap(_.execute(Garin2))
      _ <- assertEqual("completion", c, Completion.Insert(1))
      c <- s.prepare(selectCity).flatMap(_.unique(Garin2.id))
      _ <- assert("read", c == Garin2)
      _ <- s.prepare(deleteCity).flatMap(_.execute(Garin2.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("insert and delete record with contramapped command (via Contravariant instance") { s =>
    for {
      c <- s.prepare(insertCity2a).flatMap(_.execute(Garin3))
      _ <- assertEqual("completion", c, Completion.Insert(1))
      c <- s.prepare(selectCity).flatMap(_.unique(Garin3.id))
      _ <- assert("read", c == Garin3)
      _ <- s.prepare(deleteCity).flatMap(_.execute(Garin3.id))
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("merge a record") { s =>
    s.unique(sql"SHOW server_version".query(skunk.codec.all.text))
      .flatMap { version =>
        val majorVersion = version.substring(0, 2).toInt
        if (majorVersion >= 15) {
          for {
            c <- s.prepare(insertCity).flatMap(_.execute(Garin))
            _ <- assertEqual("completion", c, Completion.Insert(1))
            c <- s.prepare(mergeCity).flatMap(_.execute(Garin.id))
            _ <- assert("merge", c == Completion.Merge(1))
            c <- s.prepare(selectCity).flatMap(_.option(Garin.id))
            _ <- assert("read", c == None)
            _ <- s.execute(deleteCity)(Garin.id)
            _ <- s.assertHealthy
          } yield "ok"
        }
        else IO.pure("skip")
      }
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

  sessionTest("grant, revoke") { s =>
    for{
      _ <- s.execute(createRole)
      c <- s.execute(grant)
      _ <- assertEqual("completion", c, Completion.Grant)
      c <- s.execute(revoke)
      _ <- assertEqual("completion", c, Completion.Revoke)
      _ <- s.execute(dropRole)
      _ <- s.assertHealthy
    } yield "ok"
  }

}
