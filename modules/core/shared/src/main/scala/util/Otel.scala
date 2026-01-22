// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.effect.Resource
import cats.syntax.semigroup._
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.semconv.attributes.DbAttributes
import org.typelevel.otel4s.trace.SpanFinalizer
import org.typelevel.otel4s.trace.StatusCode
import skunk.exception.PostgresErrorException
import org.typelevel.otel4s.semconv.attributes.ErrorAttributes
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.semconv.metrics.DbMetrics
import org.typelevel.otel4s.metrics.BucketBoundaries
import org.typelevel.otel4s.metrics.Histogram

object Otel {

  // TODO the current snapshot used does not have the new `From` instance,
  // and I cannot update the otel4s dependency since skunk depends on SN 0.5
  // only available in the snapshot
  val DbSystemName = DbAttributes.DbSystemName(DbAttributes.DbSystemNameValue.Postgresql.value)

  // Similar to the default reportAbnormal strategy but records some
  // postgresql specific attributes in case it is a postgres error
  val PostgresStrategy: SpanFinalizer.Strategy = {
    case Resource.ExitCase.Errored(e: PostgresErrorException) =>
      val builder = Attributes.newBuilder

      builder += DbAttributes.DbResponseStatusCode(e.code)
      builder ++= DbAttributes.DbCollectionName.maybe(e.tableName)
      builder ++= DbAttributes.DbNamespace.maybe(e.schemaName)

      SpanFinalizer.recordException(e) |+|
        SpanFinalizer.setStatus(StatusCode.Error) |+|
        SpanFinalizer.addAttributes(builder.result())

    case Resource.ExitCase.Errored(e) =>
      SpanFinalizer.recordException(e) |+| SpanFinalizer.setStatus(StatusCode.Error)

    case Resource.ExitCase.Canceled =>
      SpanFinalizer.setStatus(StatusCode.Error, "canceled")

  }

  private val opDurationBoundaries = BucketBoundaries(0.001d, 0.005d, 0.01d, 0.05d, 0.1d, 0.5d, 1d, 5d, 10d)

  def OpDurationHistogram[F[_]: Meter]: F[Histogram[F, Double]] =
    DbMetrics.ClientOperationDuration.create[F, Double](opDurationBoundaries)

  def opDurationAttributes(exitCase: Resource.ExitCase): Attributes = {
    val builder = Attributes.newBuilder

    builder += DbSystemName

    exitCase match {
      case Resource.ExitCase.Succeeded =>

      case Resource.ExitCase.Errored(e: PostgresErrorException) =>
        builder += ErrorAttributes.ErrorType(e.getClass().getName())
        builder += DbAttributes.DbResponseStatusCode(e.code)
        builder ++= DbAttributes.DbCollectionName.maybe(e.tableName)
        builder ++= DbAttributes.DbNamespace.maybe(e.schemaName)

      case Resource.ExitCase.Errored(e) =>
        builder += ErrorAttributes.ErrorType(e.getClass().getName())

      case Resource.ExitCase.Canceled =>

    }

    builder.result()
  }

}
