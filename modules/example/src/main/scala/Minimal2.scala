// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace
import cats.data.Kleisli
import natchez.Span
import cats.temp.par._
import natchez.honeycomb.Honeycomb
import natchez.EntryPoint
// import natchez.jaeger.JaegerTracer

object Minimal2 extends IOApp {

  def session[F[_]: Concurrent: ContextShift: Trace]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world"
      // debug    = true
    )

  case class Country(code: String, name: String, pop: Int)

  val country: Decoder[Country] =
    (bpchar(3) ~ varchar ~ int4).map { case c ~ n ~ p => Country(c, n, p) }

  val select: Query[String, Country] =
    sql"""
      select code, name, population
      from country
      WHERE name like $varchar
    """.query(country)

  def lookup[F[_]: Sync: Trace](pat: String, s: Session[F]): F[Unit] =
    Trace[F].span("lookup") {
      Trace[F].put("pattern" -> pat) *>
        s.prepare(select).use { pq =>
          pq.stream(pat, 1024)
            .evalMap(c => Sync[F].delay(println(s"⭐️⭐  $c")))
            .compile
            .drain
        }
    }

  def runF[F[_]: Concurrent: ContextShift: Trace: Par]: F[ExitCode] =
    session.use { s =>
      List("A%", "B%").parTraverse(p => lookup(p, s))
    } as ExitCode.Success

  def tracer[F[_]: Sync]: Resource[F, EntryPoint[F]] =
    Honeycomb.entryPoint("skunk-example-new") { ob =>
      Sync[F].delay(
        ob.setWriteKey("<api key>")
          .setDataset("<dataset>")
          .build
      )
    }

  def run(args: List[String]): IO[ExitCode] =
    tracer[IO].use { t =>
      t.root("root").use { s =>
        runF[Kleisli[IO, Span[IO], ?]].run(s) *>
          runF[Kleisli[IO, Span[IO], ?]].run(s)
      }
    }

}
