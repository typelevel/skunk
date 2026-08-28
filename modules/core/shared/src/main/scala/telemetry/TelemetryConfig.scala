// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

/** Configures traces and metrics emitted by Skunk.
  *
  * Start with [[TelemetryConfig.default]] and use the `with` methods to change individual settings.
  */
sealed trait TelemetryConfig {
  def captureQuery: QueryCaptureConfig
  def queryAnalyzer: QueryAnalyzer
  def poolSpans: TelemetryConfig.PoolSpans
  def protocolSpans: TelemetryConfig.ProtocolSpans

  /** Changes query text and parameter capture. */
  def withCaptureQuery(captureQuery: QueryCaptureConfig): TelemetryConfig

  /** Changes the analyzer used to derive sanitized query metadata. */
  def withQueryAnalyzer(queryAnalyzer: QueryAnalyzer): TelemetryConfig

  /** Enables or disables connection-pool spans. */
  def withPoolSpans(poolSpans: TelemetryConfig.PoolSpans): TelemetryConfig

  /** Enables or disables PostgreSQL wire-protocol spans. */
  def withProtocolSpans(protocolSpans: TelemetryConfig.ProtocolSpans): TelemetryConfig
}

object TelemetryConfig {

  /** Controls spans emitted by Skunk's connection pool. */
  sealed trait PoolSpans
  object PoolSpans {

    /** Emit connection-pool operations as `INTERNAL` spans. */
    case object Internal extends PoolSpans

    /** Do not export connection-pool spans. */
    case object Disabled extends PoolSpans
  }

  /** Controls spans emitted for PostgreSQL wire-protocol operations. */
  sealed trait ProtocolSpans
  object ProtocolSpans {

    /** Emit PostgreSQL wire-protocol details as `INTERNAL` spans. */
    case object Internal extends ProtocolSpans

    /** Do not export PostgreSQL wire-protocol spans. */
    case object Disabled extends ProtocolSpans
  }

  /** Recommended defaults. Query capture is safe, protocol spans are enabled, pool spans are
    * disabled, and no query analyzer is installed.
    */
  val default: TelemetryConfig = TelemetryConfig(
    QueryCaptureConfig.recommended,
    QueryAnalyzer.noop,
    PoolSpans.Disabled,
    ProtocolSpans.Internal
  )

  /** Creates a telemetry configuration with explicit settings. */
  def apply(
      captureQuery: QueryCaptureConfig,
      queryAnalyzer: QueryAnalyzer,
      poolSpans: PoolSpans,
      protocolSpans: ProtocolSpans
  ): TelemetryConfig =
    Impl(captureQuery, queryAnalyzer, poolSpans, protocolSpans)

  private final case class Impl(
      captureQuery: QueryCaptureConfig,
      queryAnalyzer: QueryAnalyzer,
      poolSpans: PoolSpans,
      protocolSpans: ProtocolSpans
  ) extends TelemetryConfig {
    def withCaptureQuery(captureQuery: QueryCaptureConfig): TelemetryConfig =
      copy(captureQuery = captureQuery)

    def withQueryAnalyzer(queryAnalyzer: QueryAnalyzer): TelemetryConfig =
      copy(queryAnalyzer = queryAnalyzer)

    def withPoolSpans(poolSpans: PoolSpans): TelemetryConfig =
      copy(poolSpans = poolSpans)

    def withProtocolSpans(protocolSpans: ProtocolSpans): TelemetryConfig =
      copy(protocolSpans = protocolSpans)

    override def toString: String =
      s"TelemetryConfig($captureQuery, $queryAnalyzer, $poolSpans, $protocolSpans)"
  }
}
