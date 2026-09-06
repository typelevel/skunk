// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.semconv.attributes.DbAttributes
import skunk.util.Origin
import skunk.data.Type

trait Statement[A] {
  def sql:      String
  def origin:   Origin
  def encoder:  Encoder[A]
  def cacheKey: Statement.CacheKey
  def telemetry: Statement.Telemetry
}

object Statement {

  /** Explicit, typed telemetry metadata attached to a statement. */
  sealed trait Telemetry {
    /** The low-cardinality query summary, if defined. */
    def querySummary: Option[String]

    /** Additional attributes exported on the logical database span. */
    def attributes: Attributes

    /** Shortcut for `addAttributes(DbAttributes.DbQuerySummary(summary))`. */
    def withQuerySummary(summary: String): Telemetry

    /** Replaces the additional logical database span attributes. */
    def withAttributes(attributes: Attributes): Telemetry

    /** Adds or replaces additional logical database span attributes by key. */
    def addAttributes(attributes: Attribute[_]*): Telemetry
  }

  object Telemetry {
    val empty: Telemetry = Impl(Attributes.empty)

    private final case class Impl(attributes: Attributes) extends Telemetry {
      def querySummary: Option[String] =
        attributes.get(DbAttributes.DbQuerySummary).map(_.value)

      def withQuerySummary(summary: String): Telemetry =
        copy(attributes = attributes.added(DbAttributes.DbQuerySummary(summary)))

      def withAttributes(attributes: Attributes): Telemetry =
        copy(attributes = attributes)

      def addAttributes(values: Attribute[_]*): Telemetry =
        withAttributes(attributes ++ values)

      override def toString: String = s"Telemetry($attributes)"
    }
  }

  /**
   * A digest of a `Statement`, consisting only of the SQL statement and asserted input/output
   * types. This data type has lawful universal equality/hashing so we can use it as a hash key,
   * which we do internally. There is probably little use for this in end-user code.
   */
  final case class CacheKey(sql: String, encodedTypes: List[Type], decodedTypes: List[Type])

}
