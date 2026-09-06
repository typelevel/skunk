// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.testkit.InstrumentationScopeExpectation
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit
import org.typelevel.otel4s.sdk.testkit.trace.SpanExpectation
import org.typelevel.otel4s.sdk.testkit.trace.StatusExpectation
import org.typelevel.otel4s.sdk.testkit.trace.TraceExpectation
import org.typelevel.otel4s.sdk.testkit.trace.TraceExpectations
import org.typelevel.otel4s.sdk.testkit.trace.TraceForestExpectation
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.trace.TracerProvider
import skunk.telemetry.ConnectionInfo
import skunk.telemetry.Telemetry
import skunk.telemetry.TelemetryConfig

class TelemetryPoolConfigTest extends CatsEffectSuite {

  private val scope =
    InstrumentationScopeExpectation
      .name("org.typelevel.skunk")
      .version(BuildInfo.version)
      .attributesEmpty

  private def poolSpans(config: TelemetryConfig): IO[List[SpanData]] =
    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

      Telemetry
        .create[IO](config, ConnectionInfo("world", "localhost", None))
        .flatMap(_.poolSpan("pool.allocate")(IO.unit)) *>
        testkit.finishedSpans
    }

  test("pool spans are disabled by default") {
    poolSpans(TelemetryConfig.default).map(spans => assertEquals(spans, Nil))
  }

  test("pool spans can be emitted as internal spans") {
    poolSpans(
      TelemetryConfig.default.withPoolSpans(TelemetryConfig.PoolSpans.Internal)
    ).map { spans =>
      val expectation =
        TraceForestExpectation.unordered(
          TraceExpectation.leaf(
            SpanExpectation
              .internal("pool.allocate")
              .noParentSpanContext
              .attributesEmpty
              .scope(scope)
              .status(StatusExpectation.unset)
              .eventCount(0)
              .linkCount(0)
          )
        )

      TraceExpectations.check(spans, expectation) match {
        case Right(_)         => ()
        case Left(mismatches) => fail(TraceExpectations.format(mismatches))
      }
    }
  }
}
