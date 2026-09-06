// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attribute
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
import skunk.telemetry.QueryAnalyzer
import skunk.telemetry.SkunkAttributes
import skunk.telemetry.Telemetry
import skunk.telemetry.TelemetryConfig
import skunk.util.Origin
import skunk.exception.PostgresErrorException

class TelemetryQueryAnalyzerTest extends CatsEffectSuite {

  private def stringAttribute(span: SpanData, name: String): Option[String] =
    span.attributes.elements.get[String](name).map(_.value)

  private val scope =
    InstrumentationScopeExpectation
      .name("org.typelevel.skunk")
      .version(BuildInfo.version)
      .attributesEmpty

  test("disabled protocol spans do not add protocol attributes to the ambient span") {
    val config = TelemetryConfig.default
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)

    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

      for {
        telemetry <- Telemetry.create[IO](config, ConnectionInfo("world", "localhost", None))
        tracer <- tracerProvider.tracer("test").get
        _ <- tracer.span("ambient").surround {
               telemetry.internalSpan("protocol") {
                 telemetry.addProtocolAttributes(SkunkAttributes.statementId("statement"))
               }
             }
        spans <- testkit.finishedSpans
      } yield {
        val span = spans.find(_.name == "ambient").getOrElse(fail("ambient span not found"))
        assertEquals(stringAttribute(span, "skunk.statement.id"), None)
      }
    }
  }

  test("dummy analyzer values determine semantic attributes and span name") {
    val analyzer = QueryAnalyzer { _ =>
      Some(
        QueryAnalyzer.Analysis(
          queryText = Some("CALL find_country(?)"),
          storedProcedureName = Some("find_country"),
          querySummary = Some("CALL find_country")
        )
      )
    }
    val config = TelemetryConfig.default.withQueryAnalyzer(analyzer)
    val statement = Command("CALL find_country('GBR')", Origin.unknown, Void.codec)
      .addAttributes(Attribute("app.query.category", "lookup"))

    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

      for {
        telemetry <- Telemetry.create[IO](config, ConnectionInfo("world", "localhost", None))
        _ <- telemetry.databaseSpan("query", statement, Nil, RedactionStrategy.OptIn)(IO.unit)
        spans <- testkit.finishedSpans
      } yield {
        val span =
          SpanExpectation
            .client("CALL find_country")
            .noParentSpanContext
            .attributesExact(
              Attribute("db.system.name", "postgresql"),
              Attribute("db.namespace", "world"),
              Attribute("skunk.operation.name", "query"),
              Attribute("server.address", "localhost"),
              Attribute("db.query.summary", "CALL find_country"),
              Attribute("db.stored_procedure.name", "find_country"),
              Attribute("db.query.text", "CALL find_country(?)"),
              Attribute("app.query.category", "lookup")
            )
            .scope(scope)
            .status(StatusExpectation.unset)
            .eventCount(0)
            .linkCount(0)

        val expectation =
          TraceForestExpectation.unordered(TraceExpectation.leaf(span))

        TraceExpectations.check(spans, expectation) match {
          case Right(_)         => ()
          case Left(mismatches) => fail(TraceExpectations.format(mismatches))
        }
      }
    }
  }

  test("session telemetry keeps the connection namespace when an error names another schema") {
    val statement = Command("INSERT INTO country VALUES ('GBR')", Origin.unknown, Void.codec)
    val error = new PostgresErrorException(
      sql = statement.sql,
      sqlOrigin = Some(statement.origin),
      info = Map(
        'S' -> "ERROR",
        'C' -> "23505",
        'M' -> "duplicate key",
        's' -> "archive",
        't' -> "country",
      ),
      history = Nil,
    )

    OpenTelemetrySdkTestkit.inMemory[IO]().use { testkit =>
      implicit val tracerProvider: TracerProvider[IO] = testkit.tracerProvider
      implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider

      for {
        poolTelemetry <- Telemetry.create[IO](
                           TelemetryConfig.default,
                           ConnectionInfo("", "localhost", None),
                         )
        sessionTelemetry = poolTelemetry.withConnection(
                             ConnectionInfo("world", "localhost", None)
                           )
        _ <- sessionTelemetry
               .databaseSpan("query", statement, Nil, RedactionStrategy.OptIn)(
                 IO.raiseError[Unit](error)
               )
               .attempt
        spans <- testkit.finishedSpans
      } yield {
        val span = spans.headOption.getOrElse(fail("database client span not found"))
        assertEquals(stringAttribute(span, "db.namespace"), Some("world"))
        assertEquals(stringAttribute(span, "db.collection.name"), Some("country"))
      }
    }
  }
}
