// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tutorial
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.Resource
import cats.implicits._

object Encoding extends IOApp {

  import cats.effect.IO
  import skunk._
  import skunk.implicits._
  import skunk.codec.all._
  import natchez.Trace.Implicits.noop

  // IN

  // The statement depends on the size of the list because it must contain the right number of
  // placeholders.

  def select(size: Int): Query[List[String], String] = {

    // An encoder for `size` Strings. Expands to $1, $2, $3, ...
    val enc: Encoder[List[String]] =
      bpchar(3).list(size).values

    sql"""
      SELECT name
      FROM   country
      WHERE  code IN $enc
    """.query(varchar)

  }

  def in(s: Session[IO], codes: List[String]): IO[List[String]] =
    s.prepare(select(codes.size)).use { ps =>
      ps.stream(codes, 64).compile.to[List]
    }

  // VALUES .. this is excellent

  def values(s: Session[IO], vals: List[String ~ Int]): IO[Unit] = {
    val enc = (varchar ~ int4).values.list(vals.length) // ($1, $2), ($3, $4), ...
    s.prepare(sql"VALUES $enc".query(varchar ~ int4)).use { ps =>
      ps.stream(vals, 64)
        .evalMap(p => IO(println(p)))
        .compile
        .drain
    }
  }

  // What about fragment combinators?
  // we could derive syntax for (f1, f2, f2).conjoined(" AND ")
  // such that .product is just (a, b).conjoined(", ")
  // ... do we really want to go down this road?

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      debug    = true,
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        ns <- in(s, List("USA", "GBR", "FRA"))
        _  <- ns.traverse(n => IO(println(n)))
        _ <- values(s, List("foo" ~ 42 ,"bar" ~ 11, "baz" ~ 99))
      } yield ExitCode.Success
    }

}


