// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

/** Analyzes SQL text and returns semantic query metadata.
  *
  * Implementations must only return `queryText` when literals and other
  * sensitive values have been removed. The summary must be low-cardinality and
  * must not contain dynamic or sensitive values.
  *
  * In particular, analyzers may derive `querySummary` suitable for
  * `db.query.summary` span naming/cardinality rules. Custom analyzers are
  * created with [[QueryAnalyzer.apply]].
  *
  * @see
  *   [[https://opentelemetry.io/docs/specs/semconv/db/database-spans/#generating-a-summary-of-the-query]]
  */
sealed trait QueryAnalyzer { self =>

  /** Returns metadata for `sql`, or `None` when this analyzer cannot analyze it. */
  def analyze(sql: String): Option[QueryAnalyzer.Analysis]

  /** Uses `fallback` when this analyzer cannot analyze the query. */
  final def orElse(fallback: QueryAnalyzer): QueryAnalyzer =
    QueryAnalyzer { sql =>
      self.analyze(sql).orElse(fallback.analyze(sql))
    }

}

object QueryAnalyzer {

  /** Query metadata. All fields are optional because analyzers may produce
    * partial information.
    */
  sealed trait Analysis {

    /** Sanitized SQL text with literals and other sensitive values removed. */
    def queryText: Option[String]

    /** Stored procedure name for procedure-style operations. */
    def storedProcedureName: Option[String]

    /** Low-cardinality summary suitable for `db.query.summary` span naming. */
    def querySummary: Option[String]
  }

  object Analysis {

    /** Creates query metadata. An analyzer may leave any field empty. */
    def apply(
        queryText: Option[String],
        storedProcedureName: Option[String],
        querySummary: Option[String]
    ): Analysis =
      QueryMetadataImpl(
        queryText = queryText,
        storedProcedureName = storedProcedureName,
        querySummary = querySummary
      )

    private final case class QueryMetadataImpl(
        queryText: Option[String],
        storedProcedureName: Option[String],
        querySummary: Option[String]
    ) extends Analysis
  }

  /** Creates an analyzer from a function. */
  def apply(f: String => Option[Analysis]): QueryAnalyzer =
    Impl(f)

  /** An analyzer that never produces query metadata. */
  val noop: QueryAnalyzer = Impl(_ => None)

  private final case class Impl(f: String => Option[Analysis]) extends QueryAnalyzer {
    def analyze(sql: String): Option[Analysis] = f(sql)
  }

}
