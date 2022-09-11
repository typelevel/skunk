// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.syntax.all._
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import skunk.net.SSLNegotiation

abstract class SSL private[skunk] (
  val tlsParameters: TLSParameters = TLSParameters.Default,
  val fallbackOk:    Boolean       = false,
) { outer =>

  def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]]

  def withTLSParameters(tlsParameters: TLSParameters): SSL =
    new SSL(tlsParameters, fallbackOk) {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
        outer.tlsContext
    }

  def withFallback(fallbackOk: Boolean): SSL =
    new SSL(tlsParameters, fallbackOk) {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
        outer.tlsContext
    }

  def toSSLNegotiationOptions[F[_]: Network](logger: Option[String => F[Unit]])(
    implicit ev: ApplicativeError[F, Throwable]
  ): F[Option[SSLNegotiation.Options[F]]] =
    this match {
      case SSL.None => none.pure[F]
      case _ => tlsContext.map(SSLNegotiation.Options(_, tlsParameters, fallbackOk, logger).some)
    }

}

object SSL extends SSLCompanionPlatform {

  /** `SSL` which indicates that SSL is not to be used. */
  object None extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
      ev.raiseError(new Exception("SSL.None: cannot create a TLSContext."))
    override def withFallback(fallbackOk: Boolean): SSL = this
    override def withTLSParameters(tlsParameters: TLSParameters): SSL = this
  }

  /** `SSL` which trusts all certificates. */
  object Trusted extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
      Network[F].tlsContext.insecure
  }

  /** `SSL` from the system default `SSLContext`. */
  object System extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
      Network[F].tlsContext.system
  }

}
