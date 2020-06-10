// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tutorial
//#full-example
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import java.time.OffsetDateTime
import natchez.Trace.Implicits.noop

object QueryExample extends IOApp {

  // a source of sessions
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )

  // a data model
  case class Country(name: String, code: String, population: Int)

  // a simple query
  val simple: Query[Void, OffsetDateTime] =
    sql"select current_timestamp".query(timestamptz)

  // an extended query
  val extended: Query[String, Country] =
    sql"""
      SELECT name, code, population
      FROM   country
      WHERE  name like $text
    """.query(varchar ~ bpchar(3) ~ int4)
       .gmap[Country]

  // run our simple query
  def doSimple(s: Session[IO]): IO[Unit] =
    for {
      ts <- s.unique(simple) // we expect exactly one row
      _  <- IO(println(s"timestamp is $ts"))
    } yield ()

  // run our extended query
  def doExtended(s: Session[IO]): IO[Unit] =
    s.prepare(extended).use { ps =>
      ps.stream("U%", 32)
        .evalMap(c => IO(println(c)))
        .compile
        .drain
    }

  // our entry point
  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        _ <- doSimple(s)
        _ <- doExtended(s)
      } yield ExitCode.Success
    }

}
//#full-example
