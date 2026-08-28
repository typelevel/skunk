// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

/** Controls capture of query text and bound parameters. */
sealed trait QueryCaptureConfig {
  def queryTextPolicy: QueryCaptureConfig.QueryTextPolicy
  def queryParametersPolicy: QueryCaptureConfig.QueryParametersPolicy

  def withQueryTextPolicy(
      queryTextPolicy: QueryCaptureConfig.QueryTextPolicy
  ): QueryCaptureConfig

  def withQueryParametersPolicy(
      queryParametersPolicy: QueryCaptureConfig.QueryParametersPolicy
  ): QueryCaptureConfig
}

object QueryCaptureConfig {

  def apply(
      queryTextPolicy: QueryTextPolicy,
      queryParametersPolicy: QueryParametersPolicy
  ): QueryCaptureConfig =
    Impl(queryTextPolicy, queryParametersPolicy)

  sealed trait QueryTextPolicy

  object QueryTextPolicy {

    /** Never record query text. */
    case object None extends QueryTextPolicy

    /**
      * Record parameterized query text as-is. Record non-parameterized query text only when a
      * configured [[QueryAnalyzer]] supplies a sanitized value.
      */
    case object SemconvRecommended extends QueryTextPolicy

    /** Record all query text as-is. This may expose sensitive literal values. */
    case object UnsafeAlways extends QueryTextPolicy

  }

  sealed trait QueryParametersPolicy

  object QueryParametersPolicy {

    /** Never record query parameters. */
    case object None extends QueryParametersPolicy

    /** Record all query parameters. */
    case object All extends QueryParametersPolicy

  }

  /** Semantic-conventions-oriented defaults: safe query text and no parameter values. */
  val recommended: QueryCaptureConfig =
    QueryCaptureConfig(
      QueryTextPolicy.SemconvRecommended,
      QueryParametersPolicy.None
    )

  /** Query text and parameter capture are both disabled. */
  val disabled: QueryCaptureConfig =
    QueryCaptureConfig(
      QueryTextPolicy.None,
      QueryParametersPolicy.None
    )

  private final case class Impl(
      queryTextPolicy: QueryTextPolicy,
      queryParametersPolicy: QueryParametersPolicy
  ) extends QueryCaptureConfig {
    def withQueryTextPolicy(queryTextPolicy: QueryTextPolicy): QueryCaptureConfig =
      copy(queryTextPolicy = queryTextPolicy)

    def withQueryParametersPolicy(queryParametersPolicy: QueryParametersPolicy): QueryCaptureConfig =
      copy(queryParametersPolicy = queryParametersPolicy)

    override def toString: String =
      s"QueryCaptureConfig($queryTextPolicy,$queryParametersPolicy)"
  }
}
