// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.effect.Resource
import cats.syntax.all._
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import skunk.net.SSLNegotiation

abstract class SSL private[skunk] (
  val tlsParameters: TLSParameters = TLSParameters.Default,
  val fallbackOk:    Boolean       = false,
) { outer =>

  def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): Resource[F, TLSContext[F]]

  def withTLSParameters(tlsParameters: TLSParameters): SSL =
    new SSL(tlsParameters, fallbackOk) {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): Resource[F, TLSContext[F]] =
        outer.tlsContext
    }

  def withFallback(fallbackOk: Boolean): SSL =
    new SSL(tlsParameters, fallbackOk) {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): Resource[F, TLSContext[F]] =
        outer.tlsContext
    }

  def toSSLNegotiationOptions[F[_]: Network](logger: Option[String => F[Unit]])(
    implicit ev: ApplicativeError[F, Throwable]
  ): Resource[F, Option[SSLNegotiation.Options[F]]] =
    this match {
      case SSL.None => Resource.pure(None)
      case _ => tlsContext.map(SSLNegotiation.Options(_, tlsParameters, fallbackOk, logger).some)
    }

}

object SSL extends SSLCompanionPlatform {

  /** `SSL` which indicates that SSL is not to be used. */
  object None extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): Resource[F, TLSContext[F]] =
      Resource.eval(ev.raiseError(new Exception("SSL.None: cannot create a TLSContext.")))
    override def withFallback(fallbackOk: Boolean): SSL = this
    override def withTLSParameters(tlsParameters: TLSParameters): SSL = this
  }

  /** `SSL` which trusts all certificates. */
  object Trusted extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): Resource[F, TLSContext[F]] =
      Network[F].tlsContext.insecureResource
  }

  /** `SSL` from the system default `SSLContext`. */
  object System extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): Resource[F, TLSContext[F]] =
      Network[F].tlsContext.systemResource
  }

}
