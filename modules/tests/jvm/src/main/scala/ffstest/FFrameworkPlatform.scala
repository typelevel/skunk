// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import io.opentelemetry.api.GlobalOpenTelemetry
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.trace.Tracer

trait FTestPlatform extends CatsEffectSuite {
  def tracerResource: Resource[IO, Tracer[IO]] = 
    Resource.eval(IO(GlobalOpenTelemetry.get()))
      .evalMap(OtelJava.forAsync(_))
      .evalMap(_.tracerProvider.get(getClass.getName()))

  private var ioTracerFinalizer: IO[Unit] = _

  implicit lazy val ioTracer: Tracer[IO] = {
    val tracerAndFinalizer = tracerResource.allocated.unsafeRunSync()
    ioTracerFinalizer = tracerAndFinalizer._2
    tracerAndFinalizer._1
  }

  override def afterAll(): Unit = {
    if (ioTracerFinalizer ne null) {
      ioTracerFinalizer.unsafeRunSync()
      ioTracerFinalizer = null
    }
    super.afterAll()
  }
}
