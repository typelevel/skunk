```scala mdoc:invisible
import cats.effect._
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider
import skunk._
import skunk.codec.all._
import skunk.implicits._

implicit val tracerProvider: TracerProvider[IO] = TracerProvider.noop
implicit val meterProvider: MeterProvider[IO] = MeterProvider.noop
```

# Telemetry

Skunk emits OpenTelemetry database spans and metrics through
[otel4s](https://github.com/typelevel/otel4s). The instrumentation scope is
`org.typelevel.skunk`, with the Skunk version recorded as the scope version.

`Session.Builder` uses `TelemetryConfig.default` unless another configuration is supplied with
`withTelemetryConfig`. The default is the recommended starting point.

## Default behavior

The default configuration provides the following behavior:

- Simple statements, prepared statements, and cursor fetches emit semantic database `CLIENT` spans.
- Each database span has a matching
  [`db.client.operation.duration`](https://opentelemetry.io/docs/specs/semconv/db/database-metrics/#metric-dbclientoperationduration)
  histogram observation.
- PostgreSQL wire-protocol operations such as parse, bind, read, decode, and close emit diagnostic
  `INTERNAL` spans.
- Connection-pool spans are disabled.
- Parameterized query text is recorded because it does not contain bound values.
- Literal query text and query parameter values are not recorded.
- Query analysis is disabled. Skunk uses `QueryAnalyzer.noop` by default.

The configured `TracerProvider` and `MeterProvider` still decide whether signals are recorded or
exported. Supplying no-op providers disables tracing and metrics without changing
`TelemetryConfig`.

## Database spans and metrics

A database span and its duration measurement cover the same logical operation. Both include the
usual PostgreSQL attributes, such as `db.system.name`, `db.namespace`, and `server.address`.

Skunk also retains its low-cardinality execution label as `skunk.operation.name` (`query`,
`bind+execute`, or `execute`). These values describe Skunk's execution path, so Skunk does not
record them as `db.operation.name`.

Skunk records the connected database as the PostgreSQL namespace. It does not issue an additional
query to discover the session's current schema.

## Set a database span name

The most direct way to name a database span is to add a summary to a `Query` or `Command`:

```scala mdoc:silent
val findCountry =
  sql"SELECT name FROM country WHERE code = $varchar"
    .query(varchar)
    .withQuerySummary("SELECT country")
```

The resulting span is named `SELECT country` and has `db.query.summary` set to the same value. The
summary should be low-cardinality and stable across calls. Do not include IDs, argument values, or
other request-specific data.

`Command` supports the same method:

```scala mdoc:silent
val updateCountry =
  sql"UPDATE country SET name = $varchar WHERE code = $varchar"
    .command
    .withQuerySummary("UPDATE country")
```

A summary attached to a statement takes precedence over a summary returned by a
`QueryAnalyzer`. If neither is available, Skunk uses its execution label as the span name.

## Add statement attributes

Statements can carry additional attributes for the logical database span:

```scala mdoc:silent
import org.typelevel.otel4s.AttributeKey

val QueryCategory = AttributeKey[String]("app.query.category")

val findCountryWithMetadata =
  findCountry.addAttributes(QueryCategory("lookup"))
```

Application-defined attributes are not added to `db.client.operation.duration`. Avoid sensitive or
high-cardinality values. A string `db.query.summary` attribute is also used for span naming and the
operation duration metric. Attributes are accepted as-is; attributes managed by Skunk may overwrite
values with the same keys when the span is resolved.

## Configure auxiliary spans

Protocol spans are enabled by default. They describe Skunk's work below the logical database
operation and use `SpanKind.INTERNAL`. Disable them when traces should contain only database client
operations:

```scala mdoc:silent
import skunk.telemetry.TelemetryConfig

val protocolTelemetry = TelemetryConfig.default
  .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)
```

Pool spans are disabled by default. They can be enabled when investigating connection acquisition
or pool cleanup:

```scala mdoc:silent
val poolTelemetry = TelemetryConfig.default
  .withPoolSpans(TelemetryConfig.PoolSpans.Internal)
```

To state explicitly that neither category should be emitted:

```scala mdoc:silent
val minimalTelemetry = TelemetryConfig.default
  .withProtocolSpans(TelemetryConfig.ProtocolSpans.Disabled)
  .withPoolSpans(TelemetryConfig.PoolSpans.Disabled)

val sessions =
  Session.Builder[IO]
    .withTelemetryConfig(minimalTelemetry)
```

Disabling these spans does not disable semantic database spans or metrics.

## Configure query capture

`QueryCaptureConfig.recommended` is part of the default telemetry configuration:

- Parameterized query text is recorded because bound values are not present in it.
- Non-parameterized query text is omitted unless a configured analyzer supplies sanitized text.
- Query parameter values are never recorded by default.

Parameter values require an explicit global opt-in:

```scala mdoc:silent
import skunk.telemetry.{ QueryCaptureConfig, TelemetryConfig }

val parameterCaptureTelemetry = TelemetryConfig.default.withCaptureQuery(
  QueryCaptureConfig.recommended.withQueryParametersPolicy(
    QueryCaptureConfig.QueryParametersPolicy.All
  )
)

val session =
  Session.Builder[IO]
    .withTelemetryConfig(parameterCaptureTelemetry)
```

This records all bound parameters for every statement using the configuration. Captured values
still respect the session's `RedactionStrategy` and encoder-level redaction. Parameter attributes
are emitted only on spans, never as metric dimensions. Enable this only in controlled environments
where exporting parameter values is acceptable.

Disable query text and parameter capture together with `QueryCaptureConfig.disabled`:

```scala mdoc:silent
val disabledCaptureTelemetry = TelemetryConfig.default
  .withCaptureQuery(QueryCaptureConfig.disabled)
```

`QueryTextPolicy.UnsafeAlways` records SQL containing literal values. Use it only when that data is
safe to export.

## Custom query analyzers

Applications can provide an analyzer that supplies sanitized query text, a low-cardinality summary,
and a stored-procedure name. For example, an application with a small set of known statements can
define explicit metadata:

```scala mdoc:silent
import skunk.telemetry.QueryAnalyzer
import skunk.telemetry.TelemetryConfig

val analyzer = QueryAnalyzer {
  case "SELECT name FROM country WHERE code = 'GBR'" =>
    Some(QueryAnalyzer.Analysis(
      queryText = Some("SELECT name FROM country WHERE code = ?"),
      storedProcedureName = None,
      querySummary = Some("SELECT country")
    ))
  case _ =>
    None
}

val analyzerTelemetry = TelemetryConfig.default.withQueryAnalyzer(analyzer)
```

Only return `queryText` after removing literals and other sensitive values. Query summaries must be
low-cardinality and must not contain request data. Analyzer failures are ignored so they cannot
fail a database operation.

## Skunk-specific attributes

Logical database spans may include `skunk.portal.id`, `skunk.statement.id`, and
`skunk.fetch.max_rows`. Protocol spans may include statement parameter types, result column types,
row counts, and whether more rows are available. Protocol spans do not contain raw SQL or encoded
argument values.
