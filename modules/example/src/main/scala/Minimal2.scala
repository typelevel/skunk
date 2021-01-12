// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.Parallel
import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace
import cats.data.Kleisli
import natchez.Span
import natchez.jaeger.Jaeger
import natchez.EntryPoint
import io.jaegertracing.Configuration.SamplerConfiguration
import io.jaegertracing.Configuration.ReporterConfiguration
import fs2.io.net.Network
import cats.effect.std.Console

object Minimal2 extends IOApp {

  def session[F[_]: Async: Trace: Network: Console]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      // debug    = true
    )

  case class Country(code: String, name: String, pop: Int)

  val select: Query[String, Country] =
    sql"""
      select code, name, population
      from country
      WHERE name like $varchar
    """.query(bpchar(3) ~ varchar ~ int4)
       .gmap[Country]

  def lookup[F[_]: Async: Trace](pat: String, s: Session[F]): F[Unit] =
    Trace[F].span("lookup") {
      Trace[F].put("pattern" -> pat) *>
      s.prepare(select).use { pq =>
        pq.stream(pat, 1024)
          .evalMap(c => Sync[F].delay(println(s"⭐️⭐  $c")))
          .compile
          .drain
      }
    }

  def runF[F[_]: Async: Trace: Parallel: Console]: F[ExitCode] =
    session.use { s =>
      List("A%", "B%").parTraverse(p => lookup(p, s))
    } as ExitCode.Success

  def tracer[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    Jaeger.entryPoint[F]("skunk-http4s-example") { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  def run(args: List[String]): IO[ExitCode] =
    tracer[IO].use { t =>
      t.root("root").use { s =>
        runF[Kleisli[IO, Span[IO], *]].run(s) *>
        runF[Kleisli[IO, Span[IO], *]].run(s)
      }
    }

}
