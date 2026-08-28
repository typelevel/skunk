// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import munit.FunSuite
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.semconv.attributes.DbAttributes
import skunk.codec.all.varchar
import skunk.data.Encoded
import skunk.telemetry.ConnectionInfo
import skunk.telemetry.QueryAnalyzer
import skunk.telemetry.QueryCaptureConfig
import skunk.telemetry.Telemetry
import skunk.telemetry.TelemetryConfig
import skunk.util.Origin

object TestTelemetry {
  def apply(database: String)(implicit tracer: org.typelevel.otel4s.trace.Tracer[cats.effect.IO]): Telemetry[cats.effect.IO] =
    new Telemetry.Impl(
      TelemetryConfig.default,
      ConnectionInfo(database, "simulated", None),
      org.typelevel.otel4s.metrics.Histogram.noop[cats.effect.IO, Double],
    )
}

class TelemetryConfigTest extends FunSuite {

  private val connection =
    ConnectionInfo("world", "localhost", None)

  private def attribute(attributes: org.typelevel.otel4s.Attributes, name: String): Option[String] =
    attributes.get[String](name).map(_.value)

  test("telemetry records use abstract with-methods while analysis uses apply") {
    val capture = QueryCaptureConfig.recommended
      .withQueryTextPolicy(QueryCaptureConfig.QueryTextPolicy.None)
      .withQueryParametersPolicy(QueryCaptureConfig.QueryParametersPolicy.All)
    val analyzer = QueryAnalyzer.noop
    val config = TelemetryConfig.default
      .withCaptureQuery(capture)
      .withQueryAnalyzer(analyzer)
      .withPoolSpans(TelemetryConfig.PoolSpans.Internal)
      .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)
    val analysis = QueryAnalyzer.Analysis(
      queryText = Some("SELECT ?"),
      storedProcedureName = Some("find_country"),
      querySummary = Some("SELECT country")
    )
    val statementTelemetry = Statement.Telemetry.empty.withQuerySummary("SELECT nation")

    assertEquals(capture.queryTextPolicy, QueryCaptureConfig.QueryTextPolicy.None)
    assertEquals(capture.queryParametersPolicy, QueryCaptureConfig.QueryParametersPolicy.All)
    assertEquals(config.captureQuery, capture)
    assertEquals(config.queryAnalyzer, analyzer)
    assertEquals(config.poolSpans, TelemetryConfig.PoolSpans.Internal)
    assertEquals(config.protocolSpans, TelemetryConfig.ProtocolSpans.Disabled)
    assertEquals(analysis.queryText, Some("SELECT ?"))
    assertEquals(analysis.storedProcedureName, Some("find_country"))
    assertEquals(analysis.querySummary, Some("SELECT country"))
    assertEquals(statementTelemetry.querySummary, Some("SELECT nation"))
  }

  test("pool spans are disabled by default") {
    assertEquals(TelemetryConfig.default.poolSpans, TelemetryConfig.PoolSpans.Disabled)
  }

  test("QueryAnalyzer.noop and fallback") {
    val fallback = QueryAnalyzer(_ => Some(QueryAnalyzer.Analysis(None, None, Some("SELECT country"))))
    assertEquals(QueryAnalyzer.noop.analyze("SELECT 1"), None)
    assertEquals(
      QueryAnalyzer.noop.orElse(fallback).analyze("SELECT 1").flatMap(_.querySummary),
      Some("SELECT country"),
    )
  }

  test("safe defaults do not capture literal query text") {
    val statement = Command("SELECT 'secret'", Origin.unknown, Void.codec)
    val operation = Telemetry.resolveOperation(
      "query",
      statement,
      Nil,
      RedactionStrategy.OptIn,
      TelemetryConfig.default,
      connection,
    )

    assertEquals(attribute(operation.spanAttributes, "db.query.text"), None)
    assertEquals(attribute(operation.spanAttributes, "skunk.operation.name"), Some("query"))
    assertEquals(attribute(operation.spanAttributes, "db.operation.name"), None)
    assertEquals(operation.spanName, "query")
  }

  test("safe defaults capture parameterized query text but not values") {
    val statement = Command("SELECT $1::varchar", Origin.unknown, varchar)
    val operation = Telemetry.resolveOperation(
      "bind+execute",
      statement,
      List(Some(Encoded("secret"))),
      RedactionStrategy.OptIn,
      TelemetryConfig.default,
      connection,
    )

    assertEquals(attribute(operation.spanAttributes, "db.query.text"), Some(statement.sql))
    assertEquals(attribute(operation.spanAttributes, "db.query.parameter.0"), None)
    assertEquals(attribute(operation.metricAttributes, "skunk.operation.name"), Some("bind+execute"))
  }

  test("all parameter capture respects redaction and remains span-only") {
    val statement = Command("SELECT $1::varchar, $2::varchar", Origin.unknown, varchar ~ varchar)
    val config = TelemetryConfig.default.withCaptureQuery(
      QueryCaptureConfig.recommended.withQueryParametersPolicy(
        QueryCaptureConfig.QueryParametersPolicy.All
      )
    )
    val operation = Telemetry.resolveOperation(
      "bind+execute",
      statement,
      List(Some(Encoded("secret", redacted = true)), Some(Encoded("public"))),
      RedactionStrategy.OptIn,
      config,
      connection,
    )

    assertEquals(attribute(operation.spanAttributes, "db.query.parameter.0"), Some(Encoded.RedactedText))
    assertEquals(attribute(operation.spanAttributes, "db.query.parameter.1"), Some("public"))
    assertEquals(attribute(operation.metricAttributes, "db.query.parameter.0"), None)
    assertEquals(attribute(operation.metricAttributes, "db.query.parameter.1"), None)
  }

  test("analyzed text, typed summary, and connection attributes are resolved at span creation") {
    val analyzer = QueryAnalyzer(_ => Some(QueryAnalyzer.Analysis(
      queryText = Some("SELECT * FROM country WHERE code = ?"),
      storedProcedureName = None,
      querySummary = Some("ignored analyzer summary"),
    )))
    val statement =
      Command("SELECT * FROM country WHERE code = 'GBR'", Origin.unknown, Void.codec)
        .withQuerySummary("get country by code")
    val operation = Telemetry.resolveOperation(
      "query",
      statement,
      Nil,
      RedactionStrategy.OptIn,
      TelemetryConfig.default.withQueryAnalyzer(analyzer),
      connection.copy(serverPort = Some(6432L)),
    )

    assertEquals(operation.spanName, "get country by code")
    assertEquals(attribute(operation.spanAttributes, "db.system.name"), Some("postgresql"))
    assertEquals(attribute(operation.spanAttributes, "db.namespace"), Some("world"))
    assertEquals(attribute(operation.spanAttributes, "server.address"), Some("localhost"))
    assertEquals(operation.spanAttributes.get[Long]("server.port").map(_.value), Some(6432L))
    assertEquals(attribute(operation.spanAttributes, "db.query.summary"), Some("get country by code"))
    assertEquals(attribute(operation.metricAttributes, "db.query.summary"), Some("get country by code"))
    assertEquals(
      attribute(operation.spanAttributes, "db.query.text"),
      Some("SELECT * FROM country WHERE code = ?"),
    )
    assertEquals(attribute(operation.metricAttributes, "db.query.text"), None)
  }

  test("a failing analyzer cannot fail a database operation") {
    val analyzer = QueryAnalyzer(_ => throw new IllegalStateException("broken analyzer"))
    val statement = Command("SELECT 'secret'", Origin.unknown, Void.codec)
    val operation = Telemetry.resolveOperation(
      "query",
      statement,
      Nil,
      RedactionStrategy.OptIn,
      TelemetryConfig.default.withQueryAnalyzer(analyzer),
      connection,
    )

    assertEquals(operation.spanName, "query")
    assertEquals(attribute(operation.spanAttributes, "db.query.text"), None)
  }

  test("statement summaries survive transformations and are accepted as-is") {
    val command = Command("SELECT $1::varchar", Origin.unknown, varchar)
      .withQuerySummary("select value")
      .contramap[Int](_.toString)
    val query = Query("SELECT $1::varchar", Origin.unknown, varchar, varchar)
      .withQuerySummary("select value")
      .dimap[Int, Int](_.toString)(_.length)

    assertEquals(command.telemetry.querySummary, Some("select value"))
    assertEquals(query.telemetry.querySummary, Some("select value"))
    assertEquals(command.withQuerySummary("").telemetry.querySummary, Some(""))
    assertEquals(command.withQuerySummary(" " * 2).telemetry.querySummary, Some(" " * 2))
    assertEquals(command.withQuerySummary("x" * 256).telemetry.querySummary, Some("x" * 256))
  }

  test("db.query.summary can be supplied as a statement attribute") {
    val statement = Command("SELECT 1", Origin.unknown, Void.codec)
      .addAttributes(DbAttributes.DbQuerySummary("SELECT constant"))
    val operation = Telemetry.resolveOperation(
      "query",
      statement,
      Nil,
      RedactionStrategy.OptIn,
      TelemetryConfig.default,
      connection,
    )

    assertEquals(statement.telemetry.querySummary, Some("SELECT constant"))
    assertEquals(operation.spanName, "SELECT constant")
    assertEquals(attribute(operation.spanAttributes, "db.query.summary"), Some("SELECT constant"))
    assertEquals(attribute(operation.metricAttributes, "db.query.summary"), Some("SELECT constant"))
  }

  test("statement attributes are span-only and survive transformations") {
    val attributes = Attributes(Attribute("app.query.category", "lookup"))
    val statement = Command("SELECT $1::varchar", Origin.unknown, varchar)
      .withAttributes(attributes)
      .addAttributes(Attribute("db.collection.name", "country"))
      .contramap[Int](_.toString)
    val operation = Telemetry.resolveOperation(
      "bind+execute",
      statement,
      Nil,
      RedactionStrategy.OptIn,
      TelemetryConfig.default,
      connection,
    )

    assertEquals(attribute(statement.telemetry.attributes, "app.query.category"), Some("lookup"))
    assertEquals(attribute(operation.spanAttributes, "app.query.category"), Some("lookup"))
    assertEquals(attribute(operation.spanAttributes, "db.collection.name"), Some("country"))
    assertEquals(attribute(operation.metricAttributes, "app.query.category"), None)
    assertEquals(attribute(operation.metricAttributes, "db.collection.name"), None)

    val unrestricted = Statement.Telemetry.empty.addAttributes(
      Attribute("db.query.summary", 1L),
      Attribute("db.query.text", "secret"),
      Attribute("skunk.custom", "value"),
    )
    assertEquals(unrestricted.attributes.get[Long]("db.query.summary").map(_.value), Some(1L))
    assertEquals(attribute(unrestricted.attributes, "db.query.text"), Some("secret"))
    assertEquals(attribute(unrestricted.attributes, "skunk.custom"), Some("value"))
  }
}
