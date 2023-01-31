// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import fs2._
import fs2.Stream.resource
import skunk._
import skunk.implicits._
import skunk.codec.all._
import org.typelevel.otel4s.trace.Tracer

object Minimal3 extends IOApp {

  implicit val trace: Tracer[IO] = Tracer.noop

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )

  case class Country(code: String, name: String, pop: Int)

  val select =
    sql"""
      select code, name, population
      from country
      WHERE name like $varchar
    """.query(bpchar(3) ~ varchar ~ int4)
       .gmap[Country]

  def stream(pattern: String): Stream[IO, Country] =
    for {
      s  <- resource(session)
      pq <- Stream.eval(s.prepare(select))
      c  <- pq.stream(pattern, 8)
    } yield c

  def run(args: List[String]): IO[ExitCode] =
    stream("A%")
      .evalMap(c => IO(println(s"⭐️⭐  $c")))
      .compile
      .drain
      .as(ExitCode.Success)

}
