// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect._
import cats.implicits._
import fs2.io.tls.TLSContext
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.SSLContext
import fs2.io.tls.TLSParameters
import skunk.net.SSLNegotiation

sealed abstract class SSL(
  val tlsParameters: TLSParameters = TLSParameters.Default,
  val fallbackOk:    Boolean       = false,
) { outer =>

  def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext]

  def withTLSParameters(tlsParameters: TLSParameters): SSL =
    new SSL(tlsParameters, fallbackOk) {
      def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
        outer.tlsContext(b)
    }

  def withFallback(fallbackOk: Boolean): SSL =
    new SSL(tlsParameters, fallbackOk) {
      def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
        outer.tlsContext(b)
    }

  def toSSLNegotiationOptions[F[_]: Sync: ContextShift](b: Blocker, logger: Option[String => F[Unit]]): F[Option[SSLNegotiation.Options[F]]] =
    this match {
      case SSL.None => none.pure[F]
      case _ => tlsContext(b).map(SSLNegotiation.Options(_, tlsParameters, fallbackOk, logger).some)
    }

}

object SSL {

  /** `SSL` which indicates that SSL is not to be used. */
  object None extends SSL() {
    def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
      Sync[F].raiseError(new Exception("SSL.None: cannot create a TLSContext."))
      override def withFallback(fallbackOk: Boolean): SSL = this
      override def withTLSParameters(tlsParameters: TLSParameters): SSL = this
  }

  /** `SSL` which trusts all certificates. */
  object Trusted extends SSL() {
    def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
      TLSContext.insecure(b)
  }

  /** `SSL` from the system default `SSLContext`. */
  object System extends SSL() {
    def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
      TLSContext.system(b)
  }

  /** Creates a `SSL` from an `SSLContext`. */
  def fromSSLContext(ctx: SSLContext): SSL =
    new SSL() {
      def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
        TLSContext.fromSSLContext(ctx, b).pure[F]
    }

  /** Creates a `SSL` from the specified key store file. */
  def fromKeyStoreFile(
    file:          Path,
    storePassword: Array[Char],
    keyPassword:   Array[Char],
  ): SSL =
    new SSL() {
      def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
       TLSContext.fromKeyStoreFile(file, storePassword, keyPassword, b)
    }

  /** Creates a `SSL` from the specified class path resource. */
  def fromKeyStoreResource(
      resource: String,
      storePassword: Array[Char],
      keyPassword: Array[Char],
  ): SSL =
    new SSL() {
      def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
       TLSContext.fromKeyStoreResource(resource, storePassword, keyPassword, b)
    }

  /** Creates a `TLSContext` from the specified key store. */
  def fromKeyStore(
      keyStore: KeyStore,
      keyPassword: Array[Char],
  ): SSL =
    new SSL() {
      def tlsContext[F[_]: Sync: ContextShift](b: Blocker): F[TLSContext] =
       TLSContext.fromKeyStore(keyStore, keyPassword, b)
    }

}
