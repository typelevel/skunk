// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import cats.syntax.all._
import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import cats.effect._
import cats.effect.Deferred

// https://github.com/tpolecat/skunk/issues/210
class Test210 extends SkunkTest {

  // a resource that creates and drops a table
  def withPetsTable(s: Session[IO]): Resource[IO, Unit] = {
    val alloc = s.execute(sql"CREATE TABLE IF NOT EXISTS Test210_pets (name varchar, age int2)".command).void
    val free  = s.execute(sql"DROP TABLE Test210_pets".command).void
    Resource.make(alloc)(_ => free)
  }

  // a data type
  case class Pet(name: String, age: Short)

  // command to insert a pet
  val insertOne: Command[Pet] =
    sql"INSERT INTO Test210_pets VALUES ($varchar, $int2)"
      .command
      .gcontramap[Pet]

  // command to insert a specific list of Test210_pets
  def insertMany(ps: List[Pet]): Command[ps.type] = {
    val enc = (varchar ~ int2).gcontramap[Pet].values.list(ps)
    sql"INSERT INTO Test210_pets VALUES $enc".command
  }

  // query to select all Test210_pets
  def selectAll: Query[Void, Pet] =
    sql"SELECT name, age FROM Test210_pets"
      .query(varchar ~ int2)
      .gmap[Pet]

  // some sample data
  val bob     = Pet("Bob", 12)
  val beatles = List(Pet("John", 2), Pet("George", 3), Pet("Paul", 6), Pet("Ringo", 3))

  def doInserts(ready: Deferred[IO, Unit], done: Deferred[IO, Unit]): IO[Unit] =
    session.flatTap(withPetsTable).use { s =>
      for {
        _ <- s.prepare(insertOne).use(pc => pc.execute(Pet("Bob", 12)))
        _ <- s.prepare(insertMany(beatles)).use(pc => pc.execute(beatles))
        _ <- ready.complete(())
        _ <- done.get // wait for main fiber to finish
      } yield ()
    }

  val check: IO[Unit] =
    session.use { s =>
      for {
        ns <- s.execute(sql"select name from Test210_pets".query(varchar))
        _  <- assertEqual("names", ns, (bob :: beatles).map(_.name))
      } yield ()
    }

  test("issue/210") {
    for {
      ready <- Deferred[IO, Unit]
      done  <- Deferred[IO, Unit]
      fib   <- doInserts(ready, done).start // fork
      _     <- ready.get // wait for forked fiber to say it's ready
      _     <- check.guarantee {
                 // ensure the fork is cleaned up so our table gets deleted
                 done.complete(()) *> fib.join
               }
    } yield "ok"
  }

}