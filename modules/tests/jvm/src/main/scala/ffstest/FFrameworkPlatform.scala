// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.trace.Tracer

trait FTestPlatform extends CatsEffectSuite {
  def tracerResource: Resource[IO, Tracer[IO]] = 
    OtelJava.autoConfigured[IO]()
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
