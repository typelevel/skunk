// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.Tracer

trait FTestPlatform extends CatsEffectSuite {
  implicit lazy val ioTracer: Tracer[IO] = Tracer.noop
}