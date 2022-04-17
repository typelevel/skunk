// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import skunk._
import skunk.codec.all._
import skunk.implicits._
import cats.effect._
import cats.syntax.all._
import fs2._
import natchez.Trace.Implicits.noop

// This does a lot of stuff and is mostly just to test features as they're being added. This class
// will probably go away.
object Main extends IOApp {

  case class Country(name: String, code: String, indepyear: Option[Short], population: Int)

  val country: Codec[Country] =
    (varchar ~ bpchar(3) ~ int2.opt ~ int4).gimap[Country]

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  def anyLinesStdOut[F[_]: std.Console]: Pipe[F, Any, Unit] =
    _.map(_.toString).printlns

  val fra0 = sql"true"

  val frag = sql"population < $int4 AND $fra0"

  val q: Query[Int ~ String, Country] = {
    val table = "country"
    sql"""
      SELECT name, code, indepyear, population
      FROM   #$table -- literal interpolation
      WHERE  $frag   -- nested fragment
      AND    code LIKE ${bpchar(3)}
      -- and a comment at the end
    """.query(country)
  }

  def in(ncodes: Int): Query[List[String], Country] =
    sql"""
      SELECT name, code, indepyear, population
      FROM   country
      WHERE  code in (${bpchar(3).list(ncodes)})
    """.query(country)

  def clientEncodingChanged(enc: String): IO[Unit] =
    putStrLn(s">>>> CLIENT ENCODING IS NOW: $enc")

  def hmm[F[_]: Concurrent: std.Console](ps: PreparedQuery[F, Int ~ String, _]): F[Unit] =
    (ps.stream(100000 ~ "%", 4).take(25) either ps.stream(10000 ~ "%", 4))
      .through(anyLinesStdOut)
      .compile
      .drain

  val pool: SessionPool[IO] =
    Session.pooled(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      max      = 10,
    )

  def run(args: List[String]): IO[ExitCode] =
    pool.use { p =>
      p.apply(natchez.Trace[IO]).use { s =>
        for {
          f1  <- s.parameter("client_encoding").evalMap(clientEncodingChanged).compile.drain.start
          st  <- s.transactionStatus.get
          enc <- s.parameters.get.map(_.get("client_encoding"))
          _   <- putStrLn(s"Logged in! Transaction status is $st and client_encoding is $enc")
          f2  <- s.channel(id"foo").listen(10).through(anyLinesStdOut).compile.drain.start
          rs  <- s.execute(sql"select name, code, indepyear, population from country limit 20".query(country))
          _   <- rs.traverse(putStrLn)
          _   <- s.execute(sql"set seed = 0.123".command)
          _   <- s.execute(sql"set client_encoding = ISO8859_1".command)
          _   <- s.channel(id"foo").notify("here is a message")
          _   <- s.execute(sql"select current_user".query(name))
          _   <- s.prepare(q).use(hmm(_))
          _   <- s.prepare(in(3)).use { _.stream(List("FRA", "USA", "GAB"), 100).through(anyLinesStdOut).compile.drain }
          _   <- f2.cancel // otherwise it will run forever
          _   <- f1.cancel // otherwise it will run forever
          _   <- s.execute(sql"select 'x'::bpchar(10)".query(bpchar(10)))
          _   <- s.prepare(sql"select 1".query(int4)).use { _.stream(Void, 10).through(anyLinesStdOut).compile.drain }
          _   <- putStrLn("Done.")
        } yield ExitCode.Success
      } *>
      putStrLn("------------------------- STARTING SECOND SESSION --------") *>
      p.apply(natchez.Trace[IO]).use { s =>
        for {
          _   <- s.execute(sql"set seed = 0.123".command)
          _   <- s.execute(sql"set client_encoding = ISO8859_1".command)
        } yield ExitCode.Success
      }
    }

}


