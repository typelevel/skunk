// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
import cats.effect._
import fs2._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop
import org.typelevel.twiddles._

object Join extends IOApp with StreamOps {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  // A data model
  case class City(name: String, population: Int)
  case class Country(name: String, code: String, population: Int, cities: List[City])

  // A newtype for patterns, not important really
  case class Pattern(value: String)

  // A service interface
  trait WorldService[F[_]] {
    def countriesByName(pat: Pattern): Stream[F, Country]
  }

  // A service implementation
  object WorldService {

    val pattern: Encoder[Pattern] =
      varchar.gcontramap[Pattern]

    val countriesByNameQuery: Query[Pattern, ((String, String, Int), Option[City])] =
      sql"""
        SELECT country.name, country.code, country.population, city.name, city.population
        FROM country
        LEFT JOIN city ON city.countrycode = country.code
        WHERE country.name LIKE $pattern
        ORDER BY country.code, city.name ASC
      """.query((varchar *: bpchar(3) *: int4).as[(String, String, Int)] ~ (varchar *: int4).as[City].opt)

    def fromSession[F[_]](s: Session[F]): WorldService[F] =
      new WorldService[F] {
        def countriesByName(pat: Pattern): Stream[F,Country] =
          Stream.eval(s.prepare(countriesByNameQuery)).flatMap { cs =>
            cs.stream(pat, 64)
              .chunkAdjacent
              .map { case ((name, code, pop), chunk) =>
                Country(name, code, pop, chunk.toList)
              }
          }
      }

  }

  def run(args: List[String]): IO[ExitCode] =
    (session.map(WorldService.fromSession(_)) : Resource[IO,WorldService[IO]]).use { ws => // dotty requires that ascription, why?
      ws.countriesByName(Pattern("A%"))
        .evalMap(country => IO(println(country)))
        .compile
        .drain
        .as(ExitCode.Success)
    }

}

trait StreamOps {

  // A convenience. groupAdjacent by _1 and collapse optional _2 into a chuunk.
  implicit class StreamOps[F[_], A: Eq, B](s: Stream[F, (A, Option[B])]) {
    def chunkAdjacent: Stream[F, (A, Chunk[B])] =
      s.groupAdjacentBy(_._1).map { case (a, cob) => (a, cob.collect { case (_, Some(b)) => b }) }
  }

}