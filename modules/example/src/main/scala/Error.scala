// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._

object Error extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
      check    = false
    )

  def prog[F[_]: Bracket[?[_], Throwable]](s: Session[F]): F[ExitCode] =
    s.prepare {
      sql"""
        SELECT name, popsulation
        FROM   country
        WHERE  population > $varchar
        AND    population < ${int4.opt}
      """.query(varchar)
    }.use(_.unique("foo" ~ None)).as(ExitCode.Success)

  def run(args: List[String]): IO[ExitCode] =
    session.use(prog(_))
}