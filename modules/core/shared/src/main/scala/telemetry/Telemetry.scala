// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

import java.util.concurrent.TimeUnit

import cats.arrow.FunctionK
import cats.effect.{MonadCancelThrow, Resource}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.semigroup._
import cats.~>
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.metrics.{BucketBoundaries, Histogram, Meter, MeterProvider}
import org.typelevel.otel4s.semconv.attributes.{DbAttributes, ErrorAttributes, ServerAttributes}
import org.typelevel.otel4s.semconv.metrics.DbMetrics
import org.typelevel.otel4s.trace.{SpanFinalizer, SpanKind, StatusCode, Tracer, TracerProvider}
import skunk.data.Encoded
import skunk.exception.PostgresErrorException
import skunk.{BuildInfo, RedactionStrategy, Statement}

import scala.util.control.NonFatal

sealed trait Telemetry[F[_]] {

  private[skunk] def withConnection(connection: ConnectionInfo): Telemetry[F]

  private[skunk] def poolSpan[A](name: String)(fa: F[A]): F[A]

  private[skunk] def internalSpan[A](label: String)(fa: F[A]): F[A]

  private[skunk] def databaseSpan[A](
      operationName: String,
      statement: Statement[_],
      arguments: List[Option[Encoded]],
      redactionStrategy: RedactionStrategy
  )(fa: F[A]): F[A]

  private[skunk] def addAttributes(attributes: Attribute[_]*): F[Unit]

  private[skunk] def addProtocolAttributes(attributes: Attribute[_]*): F[Unit]

}

object Telemetry {

  private val DbSystemName =
    DbAttributes.DbSystemName(DbAttributes.DbSystemNameValue.Postgresql)

  private val opDurationBoundaries =
    BucketBoundaries(0.001d, 0.005d, 0.01d, 0.05d, 0.1d, 0.5d, 1d, 5d, 10d)

  private[skunk] final case class ResolvedOperation(
      spanName: String,
      spanAttributes: Attributes,
      metricAttributes: Attributes
  )

  def apply[F[_]](implicit ev: Telemetry[F]): Telemetry[F] = ev

  def create[F[_]: MonadCancelThrow: TracerProvider: MeterProvider](
      config: TelemetryConfig,
      connection: ConnectionInfo
  ): F[Telemetry[F]] =
    MeterProvider[F].meter("org.typelevel.skunk").withVersion(BuildInfo.version).get.flatMap { implicit meter: Meter[F] =>
      TracerProvider[F].tracer("org.typelevel.skunk").withVersion(BuildInfo.version).get.flatMap { implicit tracer: Tracer[F] =>
        for {
          operationDuration <- DbMetrics.ClientOperationDuration.create[F, Double](opDurationBoundaries)
        } yield new Impl(config, connection, operationDuration)
      }
    }

  private[skunk] final class Impl[F[_]: Tracer: MonadCancelThrow](
      config: TelemetryConfig,
      connection: ConnectionInfo,
      operationDuration: Histogram[F, Double]
  ) extends Telemetry[F] {

    def withConnection(connection: ConnectionInfo): Telemetry[F] =
      new Impl(config, connection, operationDuration)

    private val finalizationStrategy: SpanFinalizer.Strategy = {
      case Resource.ExitCase.Errored(e: PostgresErrorException) =>
        val builder = Attributes.newBuilder

        builder += DbAttributes.DbResponseStatusCode(e.code)
        builder += ErrorAttributes.ErrorType(e.code)
        builder ++= DbAttributes.DbCollectionName.maybe(e.tableName)

        SpanFinalizer.recordException(e) |+|
          SpanFinalizer.setStatus(StatusCode.Error) |+|
          SpanFinalizer.addAttributes(builder.result())

      case Resource.ExitCase.Errored(e) =>
        SpanFinalizer.recordException(e) |+|
          SpanFinalizer.setStatus(StatusCode.Error) |+|
          SpanFinalizer.addAttribute(
            ErrorAttributes.ErrorType(e.getClass.getName)
          )

      case Resource.ExitCase.Canceled =>
        SpanFinalizer.setStatus(StatusCode.Error, "canceled") |+|
          SpanFinalizer.addAttribute(ErrorAttributes.ErrorType("canceled"))

    }

    private val poolSpanF: String => F ~> F =
      config.poolSpans match {
        case TelemetryConfig.PoolSpans.Internal =>
          label =>
            FunctionK.liftFunction[F, F](
              Tracer[F]
                .spanBuilder(label)
                .withSpanKind(SpanKind.Internal)
                .build
                .surround
            )

        case TelemetryConfig.PoolSpans.Disabled =>
          Function.const(FunctionK.id[F])(_)
      }

    private val internalSpanF: String => F ~> F =
      config.protocolSpans match {
        case TelemetryConfig.ProtocolSpans.Internal =>
          label =>
            FunctionK.liftFunction[F, F](
              Tracer[F]
                .spanBuilder(label)
                .withSpanKind(SpanKind.Internal)
                .build
                .surround
            )

        case TelemetryConfig.ProtocolSpans.Disabled =>
          Function.const(FunctionK.id[F])(_)
      }

    def poolSpan[A](name: String)(fa: F[A]): F[A] =
      poolSpanF(name)(fa)

    def internalSpan[A](label: String)(fa: F[A]): F[A] =
      internalSpanF(label)(fa)

    def databaseSpan[A](
        operationName: String,
        statement: Statement[_],
        arguments: List[Option[Encoded]],
        redactionStrategy: RedactionStrategy
    )(fa: F[A]): F[A] = {
      val resolved = resolveOperation(
        operationName,
        statement,
        arguments,
        redactionStrategy,
        config,
        connection
      )

      Tracer[F]
        .spanBuilder(resolved.spanName)
        .withSpanKind(SpanKind.Client)
        .addAttributes(resolved.spanAttributes)
        .withFinalizationStrategy(finalizationStrategy)
        .build
        .surround {
          val attributes = operationDurationAttributes(resolved)(_)
          operationDuration
            .recordDuration(TimeUnit.SECONDS, attributes)
            .surround(fa)
        }
    }

    private[skunk] def addAttributes(attributes: Attribute[_]*): F[Unit] =
      Tracer[F].withCurrentSpanOrNoop(_.addAttributes(attributes))

    private[skunk] def addProtocolAttributes(attributes: Attribute[_]*): F[Unit] =
      config.protocolSpans match {
        case TelemetryConfig.ProtocolSpans.Internal => addAttributes(attributes: _*)
        case TelemetryConfig.ProtocolSpans.Disabled => MonadCancelThrow[F].unit
      }

    private def operationDurationAttributes(
        operation: ResolvedOperation
    )(exitCase: Resource.ExitCase): Attributes = {
      val builder = Attributes.newBuilder

      builder ++= operation.metricAttributes

      exitCase match {
        case Resource.ExitCase.Succeeded =>

        case Resource.ExitCase.Errored(e: PostgresErrorException) =>
          builder += ErrorAttributes.ErrorType(e.code)
          builder += DbAttributes.DbResponseStatusCode(e.code)
          builder ++= DbAttributes.DbCollectionName.maybe(e.tableName)

        case Resource.ExitCase.Errored(e) =>
          builder += ErrorAttributes.ErrorType(e.getClass().getName())

        case Resource.ExitCase.Canceled =>
          builder += ErrorAttributes.ErrorType("canceled")

      }

      builder.result()
    }
  }

  private[skunk] def resolveOperation(
      operationName: String,
      statement: Statement[_],
      arguments: List[Option[Encoded]],
      redactionStrategy: RedactionStrategy,
      config: TelemetryConfig,
      connection: ConnectionInfo
  ): ResolvedOperation = {
    val analysis =
      try config.queryAnalyzer.analyze(statement.sql)
      catch { case NonFatal(_) => None }

    val summary =
      statement.telemetry.querySummary
        .orElse(
          analysis
            .flatMap(_.querySummary)
            .filter(_.trim.nonEmpty)
            .map(truncateSummary)
        )

    val storedProcedure =
      analysis.flatMap(_.storedProcedureName).filter(_.nonEmpty)

    val common = Attributes.newBuilder
    common += DbSystemName
    common += DbAttributes.DbNamespace(connection.database)
    common += SkunkAttributes.operationName(operationName)
    common += ServerAttributes.ServerAddress(connection.serverAddress)
    connection.serverPort.foreach(p => common += ServerAttributes.ServerPort(p))
    summary.foreach(s => common += DbAttributes.DbQuerySummary(s))
    storedProcedure.foreach(p =>
      common += DbAttributes.DbStoredProcedureName(p)
    )

    val metricAttributes = common.result()
    val span = Attributes.newBuilder
    span ++= statement.telemetry.attributes
    span ++= metricAttributes

    val isParameterized = statement.encoder.types.nonEmpty
    val queryTextPolicy = config.captureQuery.queryTextPolicy
    if (queryTextPolicy == QueryCaptureConfig.QueryTextPolicy.SemconvRecommended) {
      val queryText =
        if (isParameterized) Some(statement.sql)
        else analysis.flatMap(_.queryText)
      queryText
        .filter(_.nonEmpty)
        .foreach(q => span += DbAttributes.DbQueryText(q))
    } else if (queryTextPolicy == QueryCaptureConfig.QueryTextPolicy.UnsafeAlways) {
      span += DbAttributes.DbQueryText(statement.sql)
    }

    config.captureQuery.queryParametersPolicy match {
      case QueryCaptureConfig.QueryParametersPolicy.All =>
        redactionStrategy.redactArguments(arguments).zipWithIndex.foreach {
          case (argument, index) =>
            val value = argument.fold("NULL")(_.toString)
            span.addOne(s"db.query.parameter.$index", value)
        }
      case _ =>
    }

    ResolvedOperation(
      spanName = summary.orElse(storedProcedure).getOrElse(operationName),
      spanAttributes = span.result(),
      metricAttributes = metricAttributes
    )
  }

  private def truncateSummary(summary: String): String =
    if (summary.length <= 255) summary
    else {
      val lastSpace = summary.lastIndexOf(' ', 255)
      if (lastSpace > 0) summary.substring(0, lastSpace)
      else summary.substring(0, 255)
    }

}
