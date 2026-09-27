// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

import cats.arrow.FunctionK
import cats.syntax.functor._
import cats.{Monad, ~>}
import org.typelevel.otel4s.trace.{SpanKind, Tracer, TracerProvider}
import skunk.BuildInfo

sealed trait PoolTelemetry[F[_]] {

  private[skunk] def span[A](name: String)(fa: F[A]): F[A]

}

object PoolTelemetry {

  sealed trait Config {
    def poolSpans: Config.PoolSpans

    /** Enables or disables connection-pool spans. */
    def withPoolSpans(poolSpans: Config.PoolSpans): Config
  }

  object Config {

    /** Controls spans emitted by Skunk's connection pool. */
    sealed trait PoolSpans
    object PoolSpans {

      /** Emit connection-pool operations as `INTERNAL` spans. */
      case object Internal extends PoolSpans

      /** Do not export connection-pool spans. */
      case object Disabled extends PoolSpans
    }

    /** Recommended defaults. Pool spans are disabled.
      */
    val default: Config = Config(PoolSpans.Disabled)

    /** Creates a telemetry configuration with explicit settings. */
    def apply(poolSpans: PoolSpans): Config =
      Impl(poolSpans)

    private final case class Impl(poolSpans: PoolSpans) extends Config {
      def withPoolSpans(poolSpans: Config.PoolSpans): Config =
        copy(poolSpans = poolSpans)

      override def toString: String =
        s"PoolTelemetry.Config($poolSpans)"
    }

  }

  def apply[F[_]](implicit ev: PoolTelemetry[F]): PoolTelemetry[F] = ev

  def create[F[_]: Monad: TracerProvider](config: Config): F[PoolTelemetry[F]] =
    TracerProvider[F].tracer("org.typelevel.skunk").withVersion(BuildInfo.version).get.map { implicit tracer =>
      new Impl(config)
    }

  def noop[F[_]]: PoolTelemetry[F] =
    new Noop[F]

  private final class Impl[F[_]: Tracer](config: Config) extends PoolTelemetry[F] {
    private val spanF: String => F ~> F =
      config.poolSpans match {
        case Config.PoolSpans.Internal =>
          label =>
            FunctionK.liftFunction[F, F](
              Tracer[F]
                .spanBuilder(label)
                .withSpanKind(SpanKind.Internal)
                .build
                .surround
            )
        case Config.PoolSpans.Disabled =>
          Function.const(FunctionK.id[F])(_)
      }

    private[skunk] def span[A](name: String)(fa: F[A]): F[A] = spanF(name)(fa)
  }

  private final class Noop[F[_]] extends PoolTelemetry[F] {
    def span[A](name: String)(fa: F[A]): F[A] = fa
  }

}
