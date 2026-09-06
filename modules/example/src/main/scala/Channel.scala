// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import skunk._
import skunk.implicits._
import org.typelevel.otel4s.trace.TracerProvider
import org.typelevel.otel4s.metrics.MeterProvider

object Channel extends IOApp {

  implicit val tracerProvider: TracerProvider[IO] = TracerProvider.noop
  implicit val meterProvider: MeterProvider[IO] = MeterProvider.noop

  val session: Resource[IO, Session[IO]] =
    Session.Builder[IO]
      .withUserAndPassword("jimmy", "banana")
      .withDatabase("world")
      .single

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      s.channel(ident"foo")
       .listen(42)
       .take(3)
       .evalMap(n => IO.println(s"⭐️⭐  $n"))
       .compile
       .drain
    } as ExitCode.Success

}
