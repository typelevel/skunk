# Tracing

Skunk uses structured tracing instead of logging, using the [otel4s](https://github.com/typelevel/otel4s) tracing library.

## I Don't Care

If you don't care about tracing you have two choices:

- Use the **no-op tracer** to disable tracing entirely (`org.typelevel.otel4s.Tracer.noop`).
- Use the **log tracer** to log completed traces to a [log4cats](https://typelevel.org/log4cats/) logger.

## Tracing with Jaeger

Easy because there's a docker container.

## Tracing Example

... program that adds its own events to the trace

