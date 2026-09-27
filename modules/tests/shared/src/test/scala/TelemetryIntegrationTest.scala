// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.BucketBoundaries
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.metrics.data.MetricData
import org.typelevel.otel4s.sdk.testkit.InstrumentationScopeExpectation
import org.typelevel.otel4s.sdk.testkit.OpenTelemetrySdkTestkit
import org.typelevel.otel4s.sdk.testkit.metrics.MetricExpectation
import org.typelevel.otel4s.sdk.testkit.metrics.MetricExpectations
import org.typelevel.otel4s.sdk.testkit.metrics.PointExpectation
import org.typelevel.otel4s.sdk.testkit.trace.SpanExpectation
import org.typelevel.otel4s.sdk.testkit.trace.StatusExpectation
import org.typelevel.otel4s.sdk.testkit.trace.TraceExpectation
import org.typelevel.otel4s.sdk.testkit.trace.TraceExpectations
import org.typelevel.otel4s.sdk.testkit.trace.TraceForestExpectation
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.trace.TracerProvider
import skunk.codec.all.int4
import skunk.codec.all.varchar
import skunk.implicits._
import skunk.telemetry.QueryCaptureConfig
import skunk.telemetry.TelemetryConfig

class TelemetryIntegrationTest extends CatsEffectSuite {

  private val ScopeName = "org.typelevel.skunk"
  private val ScopeVersion = BuildInfo.version
  private val DurationMetricName = "db.client.operation.duration"
  private val DurationBoundaries =
    BucketBoundaries(0.001d, 0.005d, 0.01d, 0.05d, 0.1d, 0.5d, 1d, 5d, 10d)

  private val scope =
    InstrumentationScopeExpectation
      .name(ScopeName)
      .version(ScopeVersion)
      .attributesEmpty

  private def session(
      config: TelemetryConfig
  ): Resource[IO, (Session[IO], OpenTelemetrySdkTestkit[IO])] =
    OpenTelemetrySdkTestkit.inMemory[IO]().flatMap { testkit =>
      implicit val T: TracerProvider[IO] = testkit.tracerProvider
      implicit val M: MeterProvider[IO] = testkit.meterProvider
      Session.Builder[IO]
        .withUserAndPassword("jimmy", "banana")
        .withDatabase("world")
        .withTelemetryConfig(config)
        .single
        .map((_, testkit))
    }

  private def commonAttributes(
      summary: String,
      operationName: String
  ): List[Attribute[_]] =
    List(
      Attribute("db.system.name", "postgresql"),
      Attribute("db.namespace", "world"),
      Attribute("server.address", "localhost"),
      Attribute("db.query.summary", summary),
      Attribute("skunk.operation.name", operationName)
    )

  private def attributeNames(span: SpanData): Set[String] =
    span.attributes.elements.iterator.map(_.key.name).toSet

  private def stringAttribute(span: SpanData, name: String): Option[String] =
    span.attributes.elements.get[String](name).map(_.value)

  private def longAttribute(span: SpanData, name: String): Option[Long] =
    span.attributes.elements.get[Long](name).map(_.value)

  private def hasNonEmptyString(span: SpanData, name: String): Boolean =
    stringAttribute(span, name).exists(_.nonEmpty)

  private def successfulClient(
      name: String,
      attributes: List[Attribute[_]]
  ): SpanExpectation =
    SpanExpectation
      .client(name)
      .noParentSpanContext
      .attributesExact(attributes: _*)
      .scope(scope)
      .status(StatusExpectation.unset)
      .eventCount(0)
      .linkCount(0)

  private def durationPoint(
      attributes: List[Attribute[_]]
  ): PointExpectation.Histogram =
    PointExpectation.histogram
      .count(1L)
      .boundaries(DurationBoundaries)
      .attributesExact(attributes: _*)

  private def durationMetric(
      first: PointExpectation.Histogram,
      rest: PointExpectation.Histogram*
  ): MetricExpectation =
    MetricExpectation
      .histogram(DurationMetricName)
      .unit("s")
      .scope(scope)
      .exactlyPoints(first, rest: _*)

  private def assertTrace(
      spans: List[SpanData],
      relevantNames: Set[String],
      expectation: TraceForestExpectation
  ): Unit = {
    val relevantSpans = spans.filter(span => relevantNames(span.name))
    TraceExpectations.check(relevantSpans, expectation) match {
      case Right(_)         => ()
      case Left(mismatches) => fail(TraceExpectations.format(mismatches))
    }
  }

  private def assertMetrics(
      metrics: List[MetricData],
      expectation: MetricExpectation
  ): Unit = {
    val operationMetrics = metrics.filter(_.name == DurationMetricName)
    assertEquals(operationMetrics.length, 1)
    MetricExpectations.checkAll(operationMetrics, expectation) match {
      case Right(_)         => ()
      case Left(mismatches) => fail(MetricExpectations.format(mismatches))
    }
  }

  test("simple execution emits one safe semantic span and one matching metric point") {
    val config = TelemetryConfig.default
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)
    session(config).use { case (session, testkit) =>
      val query = sql"SELECT 'secret'::varchar".query(varchar).withQuerySummary("SELECT constant")
      val attributes = commonAttributes("SELECT constant", "query")
      for {
        result <- session.unique(query)
        spans <- testkit.finishedSpans
        metrics <- testkit.collectMetrics
      } yield {
        assertEquals(result, "secret")
        assertTrace(
          spans,
          Set("SELECT constant"),
          TraceForestExpectation.unordered(
            TraceExpectation.leaf(successfulClient("SELECT constant", attributes))
          )
        )
        assertMetrics(metrics, durationMetric(durationPoint(attributes)))
      }
    }
  }

  test("prepared and cursor executions capture all parameters at each logical boundary") {
    val config = TelemetryConfig.default
      .withCaptureQuery(
        QueryCaptureConfig.recommended.withQueryParametersPolicy(
          QueryCaptureConfig.QueryParametersPolicy.All
        )
      )
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)
    session(config).use { case (session, testkit) =>
      val command =
        sql"UPDATE country SET name = name WHERE code = $varchar".command
          .withQuerySummary("UPDATE country")
      val query =
        sql"SELECT name FROM country WHERE code = $varchar".query(varchar)
          .withQuerySummary("SELECT country")
      val commandMetricAttributes = commonAttributes("UPDATE country", "bind+execute")
      val queryMetricAttributes = commonAttributes("SELECT country", "execute")
      val commandStaticAttributes = commandMetricAttributes ++ List(
        Attribute("db.query.text", "UPDATE country SET name = name WHERE code = $1"),
        Attribute("db.query.parameter.0", "GBR")
      )
      val queryStaticAttributes = queryMetricAttributes ++ List(
        Attribute("db.query.text", "SELECT name FROM country WHERE code = $1"),
        Attribute("db.query.parameter.0", "GBR"),
        Attribute("skunk.fetch.max_rows", 1L)
      )
      val commandKeys = commandStaticAttributes.map(_.key.name).toSet ++
        Set("skunk.portal.id", "skunk.statement.id")
      val queryKeys = queryStaticAttributes.map(_.key.name).toSet ++
        Set("skunk.portal.id", "skunk.statement.id")

      val commandSpan =
        SpanExpectation
          .client("UPDATE country")
          .noParentSpanContext
          .attributesSubset(commandStaticAttributes: _*)
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)
          .where("exact dynamic command attributes") { span =>
            attributeNames(span) == commandKeys &&
            hasNonEmptyString(span, "skunk.portal.id") &&
            hasNonEmptyString(span, "skunk.statement.id")
          }

      val querySpan =
        SpanExpectation
          .client("SELECT country")
          .noParentSpanContext
          .attributesSubset(queryStaticAttributes: _*)
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)
          .where("exact dynamic cursor attributes") { span =>
            attributeNames(span) == queryKeys &&
            hasNonEmptyString(span, "skunk.portal.id") &&
            hasNonEmptyString(span, "skunk.statement.id")
          }

      for {
        _ <- session.execute(command)("GBR")
        rows <- session.prepare(query).flatMap(_.cursor("GBR").use(_.fetch(1)))
        spans <- testkit.finishedSpans
        metrics <- testkit.collectMetrics
      } yield {
        assertEquals(rows._1, List("United Kingdom"))
        assertTrace(
          spans,
          Set("UPDATE country", "SELECT country"),
          TraceForestExpectation.unordered(
            TraceExpectation.leaf(commandSpan),
            TraceExpectation.leaf(querySpan)
          )
        )
        assertMetrics(
          metrics,
          durationMetric(
            durationPoint(commandMetricAttributes),
            durationPoint(queryMetricAttributes)
          )
        )
      }
    }
  }

  test("prepare failures emit a protocol span when protocol tracing is enabled") {
    val config = TelemetryConfig.default
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Internal)
    session(config).use { case (session, testkit) =>
      val query =
        sql"SELECT name FROM telemetry_missing_table WHERE code = $varchar"
          .query(varchar)
          .withQuerySummary("SELECT missing table")
      val span =
        SpanExpectation
          .internal("parse+describe")
          .noParentSpanContext
          .scope(scope)
          .status(StatusExpectation.error)
          .eventCount(1)
          .linkCount(0)

      for {
        _ <- session.prepare(query).attempt
        spans <- testkit.finishedSpans
        metrics <- testkit.collectMetrics
      } yield {
        assertTrace(
          spans,
          Set("parse+describe"),
          TraceForestExpectation.unordered(TraceExpectation.leaf(span))
        )
        assertEquals(metrics.filter(_.name == DurationMetricName), Nil)
      }
    }
  }

  test("PostgreSQL errors use SQLSTATE on the semantic span and metric") {
    val config = TelemetryConfig.default
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)
    session(config).use { case (session, testkit) =>
      val query = sql"SELECT 1 / 0".query(int4).withQuerySummary("divide by zero")
      val attributes = commonAttributes("divide by zero", "query") ++ List(
        Attribute("error.type", "22012"),
        Attribute("db.response.status_code", "22012")
      )
      val errorSpan =
        SpanExpectation
          .client("divide by zero")
          .noParentSpanContext
          .attributesExact(attributes: _*)
          .scope(scope)
          .status(StatusExpectation.error)
          .eventCount(1)
          .linkCount(0)

      for {
        _ <- session.unique(query).attempt
        spans <- testkit.finishedSpans
        metrics <- testkit.collectMetrics
      } yield {
        assertTrace(
          spans,
          Set("divide by zero"),
          TraceForestExpectation.unordered(TraceExpectation.leaf(errorSpan))
        )
        assertMetrics(metrics, durationMetric(durationPoint(attributes)))
      }
    }
  }

  test("protocol spans retain namespaced Skunk details without SQL or arguments") {
    val config = TelemetryConfig.default
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Internal)
    session(config).use { case (session, testkit) =>
      val query =
        sql"SELECT name FROM country WHERE code = $varchar".query(varchar)
          .withQuerySummary("SELECT country")
      val clientStaticAttributes = commonAttributes("SELECT country", "execute") ++ List(
        Attribute("db.query.text", "SELECT name FROM country WHERE code = $1"),
        Attribute("skunk.fetch.max_rows", 1L)
      )
      val clientKeys = clientStaticAttributes.map(_.key.name).toSet ++
        Set("skunk.portal.id", "skunk.statement.id")

      val prepareKeys = Set(
        "skunk.statement.id",
        "skunk.statement.parameter_types",
        "skunk.result.column_types"
      )

      val prepare =
        SpanExpectation
          .internal("parse+describe")
          .noParentSpanContext
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)
          .where("exact prepare attributes") { span =>
            attributeNames(span) == prepareKeys &&
            hasNonEmptyString(span, "skunk.statement.id") &&
            hasNonEmptyString(span, "skunk.statement.parameter_types") &&
            hasNonEmptyString(span, "skunk.result.column_types")
          }

      val client =
        SpanExpectation
          .client("SELECT country")
          .noParentSpanContext
          .attributesSubset(clientStaticAttributes: _*)
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)
          .where("exact dynamic cursor attributes") { span =>
            attributeNames(span) == clientKeys &&
            hasNonEmptyString(span, "skunk.portal.id") &&
            hasNonEmptyString(span, "skunk.statement.id")
          }

      val read =
        SpanExpectation
          .internal("read")
          .attributesSubset(Attribute("skunk.response.row_count", 1L))
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)
          .where("exact read response attributes") { span =>
            attributeNames(span) == Set(
              "skunk.response.row_count",
              "skunk.response.more_rows"
            ) && longAttribute(span, "skunk.response.row_count").contains(1L)
          }

      val decode =
        SpanExpectation
          .internal("decode")
          .attributesEmpty
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)

      val close =
        SpanExpectation
          .internal("close-portal")
          .noParentSpanContext
          .scope(scope)
          .status(StatusExpectation.unset)
          .eventCount(0)
          .linkCount(0)
          .where("exact close portal attributes") { span =>
            attributeNames(span) == Set("skunk.portal.id") &&
            hasNonEmptyString(span, "skunk.portal.id")
          }

      for {
        rows <- session.prepare(query).flatMap(_.cursor("GBR").use(_.fetch(1)))
        spans <- testkit.finishedSpans
      } yield {
        assertEquals(rows._1, List("United Kingdom"))
        assertTrace(
          spans,
          Set("parse+describe", "SELECT country", "read", "decode", "close-portal"),
          TraceForestExpectation.unordered(
            TraceExpectation.leaf(prepare),
            TraceExpectation.unordered(
              client,
              TraceExpectation.leaf(read),
              TraceExpectation.leaf(decode)
            ),
            TraceExpectation.leaf(close)
          )
        )
      }
    }
  }
}
