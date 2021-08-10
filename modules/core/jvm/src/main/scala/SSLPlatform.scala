// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.syntax.all._
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.SSLContext
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext

private[skunk] trait SSLCompanionPlatform { this: SSL.type =>
  
  /** `SSL` which trusts all certificates. */
  object Trusted extends SSL {
    def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
      Network[F].tlsContext.insecure
  }

  /** Creates a `SSL` from an `SSLContext`. */
  def fromSSLContext(ctx: SSLContext): SSL =
    new SSL {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
        Network[F].tlsContext.fromSSLContext(ctx).pure[F]
    }

  /** Creates a `SSL` from the specified key store file. */
  def fromKeyStoreFile(
    file:          Path,
    storePassword: Array[Char],
    keyPassword:   Array[Char],
  ): SSL =
    new SSL {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
       Network[F].tlsContext.fromKeyStoreFile(file, storePassword, keyPassword)
    }

  /** Creates a `SSL` from the specified class path resource. */
  def fromKeyStoreResource(
      resource: String,
      storePassword: Array[Char],
      keyPassword: Array[Char],
  ): SSL =
    new SSL {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
       Network[F].tlsContext.fromKeyStoreResource(resource, storePassword, keyPassword)
    }

  /** Creates a `TLSContext` from the specified key store. */
  def fromKeyStore(
      keyStore: KeyStore,
      keyPassword: Array[Char],
  ): SSL =
    new SSL {
      def tlsContext[F[_]: Network](implicit ev: ApplicativeError[F, Throwable]): F[TLSContext[F]] =
       Network[F].tlsContext.fromKeyStore(keyStore, keyPassword)
    }

}
