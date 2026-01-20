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

object Otel {

  val DbSystemName = DbAttributes.DbSystemName("postgresql")

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

}
