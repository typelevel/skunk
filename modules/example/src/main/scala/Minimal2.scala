// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer
import fs2.io.net.Network
import cats.effect.std.Console

object Minimal2 extends IOApp {


  def session[F[_]: Temporal: Tracer: Meter: Console: Network]: Resource[F, Session[F]] =
    Session.Builder[F]
      .withUserAndPassword("jimmy", "banana")
      .withDatabase("world")
      .single

  case class Country(code: String, name: String, pop: Int)

  val select: Query[String, Country] =
    sql"""
      select code, name, population
      from country
      WHERE name like $varchar
    """.query(bpchar(3) *: varchar *: int4)
       .to[Country]

  def lookup[F[_]: Concurrent: Tracer: Console](pat: String, s: Session[F]): F[Unit] =
    Tracer[F].span("lookup").use { span =>
      span.addAttribute(Attribute("pattern", pat)) *>
      s.prepare(select).flatMap { pq =>
        pq.stream(pat, 1024)
          .evalMap(c => Console[F].println(s"⭐️⭐  $c"))
          .compile
          .drain
      }
    }

  def runF[F[_]: Temporal: Tracer: Meter: Console: Network]: F[ExitCode] =
    session.use { s =>
      List("A%", "B%").parTraverse(p => lookup(p, s))
    } as ExitCode.Success

  def getTelemetry[F[_]: Async: LiftIO]: Resource[F, (Tracer[F], Meter[F])] =
    OtelJava.autoConfigured[F]()
      .evalMap{ otel =>
        (otel.tracerProvider.tracer("skunk-http4s-example").get, otel.meterProvider.meter("skunk-http4s-example").get).tupled
      }

  def run(args: List[String]): IO[ExitCode] =
    getTelemetry[IO].use { case (tracer, meter) =>
      implicit val M = meter
      implicit val T = tracer
      T.span("root").surround {
        runF[IO] *> runF[IO]
      }
    }

}
