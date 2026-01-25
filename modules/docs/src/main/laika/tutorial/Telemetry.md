# Telemetry

Skunk uses [OpenTelemtry](https://opentelemetry.io/), using the [otel4s](https://github.com/typelevel/otel4s) implementation.

## Metrics

Skunk provides the [`db.client.operation.duration`](https://opentelemetry.io/docs/specs/semconv/db/database-metrics/#metric-dbclientoperationduration) histogram, that records the duration of all operations interacting with the postgresql server.

### I Don't Care

If you don't care about metrics you can use the **no-op meter** to disable metrics entirely (`org.typelevel.otel4s.metrics.Meter`).

## Tracing

### I Don't Care

If you don't care about tracing you have two choices:

- Use the **no-op tracer** to disable tracing entirely (`org.typelevel.otel4s.Tracer.noop`).
- Use the **log tracer** to log completed traces to a [log4cats](https://typelevel.org/log4cats/) logger.

### Tracing with Jaeger

Easy because there's a docker container.

### Tracing Example

... program that adds its own events to the trace

