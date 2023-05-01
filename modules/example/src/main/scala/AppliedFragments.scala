// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop

object AppliedFragments extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  def countryQuery(name: Option[String], pop: Option[Int], capital: Option[Option[String]]): AppliedFragment = {

    // Our base query
    val base = sql"SELECT name, population, capital FROM country"

    // Some filter conditions
    val nameLike       = sql"name LIKE $varchar"
    val popGreaterThan = sql"population > $int4"

    // A conditional filter condition
    def capitalCond(c: Option[String]) = c.fold(void"capital IS NULL")(sql"capital = $varchar")

    // All our conditions, applied to arguments.
    val conds: List[AppliedFragment] =
      List(
        capital.map(capitalCond),
        name.map(nameLike),
        pop .map(popGreaterThan),
      ).flatten

    // The composed filter.
    val filter =
      if (conds.isEmpty) AppliedFragment.empty
      else conds.foldSmash(void" WHERE ", void" AND ", AppliedFragment.empty)

    // Prepend the base query and we're done.
    base(Void) |+| filter

  }

  case class Country(name: String, pop: Int, capital: Option[Int])
  val country = (varchar *: int4 *: int4.opt).as[Country]

  def topFive(s: Session[IO], name: Option[String], pop: Option[Int], capital: Option[Option[String]]): IO[Unit] =
    for {
      _  <- IO.println(s"\nargs: $name, $pop, $capital")
      af  = countryQuery(name, pop, capital)
      _ <- IO.println(af.fragment.sql)
      _ <- s.prepare(af.fragment.query(country)).flatMap(_.stream(af.argument, 64).take(5).evalMap(c => IO.println(c)).compile.drain)
    } yield ()

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        _ <- topFive(s, None, None, None)
        _ <- topFive(s, Some("B%"), None, None)
        _ <- topFive(s, Some("B%"), None, Some(None))
      } yield ExitCode.Success
    }

}