// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tutorial
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.Resource

object Query extends IOApp {

  //#query-a
  import cats.effect.IO
  import fs2.Stream
  import skunk._
  import skunk.implicits._
  import skunk.codec.all._

  val a: Query[Void, String] =
    sql"SELECT name FROM country".query(varchar)
  //#query-a

  def q_a_exec[F[_]](s: Session[IO]): IO[List[String]] = {
    //#query-a-exec
    // assume s: Session[IO]
    s.execute(a) // IO[List[String]]
    //#query-a-exec
  }

  //#query-b
  val b: Query[Void, String ~ Int] =
    sql"SELECT name, population FROM country".query(varchar ~ int4)
  //#query-b

  //#query-c
  case class Country(name: String, population: Int)

  val c: Query[Void, Country] =
    sql"SELECT name, population FROM country"
      .query(varchar ~ int4)
      .map { case n ~ p => Country(n, p) }
  //#query-c

  //#query-d
  val country: Decoder[Country] =
    (varchar ~ int4).map { case (n, p) => Country(n, p) }

  val d: Query[Void, Country] =
    sql"SELECT name, population FROM country".query(country)
  //#query-d

  def q_d_exec[F[_]](s: Session[IO]): IO[List[Country]] = {
    //#query-d-exec
    // assume s: Session[IO]
    s.execute(d) // IO[List[Country]]
    //#query-d-exec
  }

  //#query-e
  val e: Query[String, Country] =
    sql"""
      SELECT name, population
      FROM   country
      WHERE  name LIKE $varchar
    """.query(country)
  //#query-e

  def q_e_exec_a(s: Session[IO]): IO[Unit] = {
    //#query-e-exec-a
    // assume s: Session[IO]
    s.prepare(e).use { ps =>
      ps.stream("U%", 64)
        .evalMap(c => IO(println(c)))
        .compile
        .drain
    } // IO[Unit]
    //#query-e-exec-a
  }

  def q_e_exec_b(s: Session[IO]): IO[Unit] = {
    //#query-e-exec-b
    // assume s: Session[IO]
    val stream: Stream[IO, Unit] =
      for {
        ps <- Stream.resource(s.prepare(e))
        c  <- ps.stream("U%", 64)
        _  <- Stream.eval(IO(println(c)))
      } yield ()

    stream.compile.drain // IO[Unit]
    //#query-e-exec-b
  }


  //#query-f
  val f: Query[String ~ Int, Country] =
    sql"""
      SELECT name, population
      FROM   country
      WHERE  name LIKE $varchar
      AND    population < $int4
    """.query(country)
  //#query-f

  def q_f_exec(s: Session[IO]): IO[Unit] = {
    //#query-f-exec
    // assume s: Session[IO]
    s.prepare(f).use { ps =>
      ps.stream("U%" ~ 2000000, 64)
        .evalMap(c => IO(println(c)))
        .compile
        .drain
    } // IO[Unit]
    //#query-f-exec
  }

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "postgres",
      database = "world",
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        _ <- s.execute(a)
        _ <- s.execute(b)
        _ <- s.execute(c)
        _ <- s.prepare(e).use { q =>
          q.stream("U%", 128).compile.drain
        }
        _ <- s.prepare(f).use { q =>
          q.stream("U%" ~ 100, 128).compile.drain
        }
      } yield ExitCode.Success
    }

}


