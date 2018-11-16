// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._

object Minimal2 extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  case class Country(code: String, name: String, pop: Long)

  val country: Decoder[Country] =
    (varchar ~ varchar ~ int8).map { case c ~ n ~ p => Country(c, n, p) }

  val select =
    sql"""
      select code, name, population
      from country
      WHERE name like $varchar
    """.query(country)

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      s.prepare(select).use { pq =>
        pq.stream("A%", 8)
          .evalMap(c => IO(println(s"⭐️⭐ $c")))
          .compile
          .drain
      }
    } as ExitCode.Success

}