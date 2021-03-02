// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import fs2.io.tls.TLSContext
import fs2.io.tls.TLSParameters
import cats._
import cats.effect._
import cats.syntax.all._
import fs2.io.tcp._
import scala.concurrent.duration._
import fs2.Chunk

object SSLNegotiation {

  /** Parameters for `negotiateSSL`. */
  case class Options[F[_]](
    tlsContext:    TLSContext,
    tlsParameters: TLSParameters,
    fallbackOk:    Boolean,
    logger:        Option[String => F[Unit]],
  )

  // SSLRequest message, as a Chunk[Byte]
  val SSLRequest: Chunk[Byte] =
    Chunk.array(Array[Byte](0, 0, 0, 8, 4, -46, 22, 47))

  /**
   * Negotiate SSL with Postgres, given a brand new connected `Socket` and a `TLSContext`. If SSL is
   * unavailable, fall back to the unencrypted socket if `fallbackOk`, otherwise raise an exception.
   */
  def negotiateSSL[F[_]: Concurrent: ContextShift](
    socket:       Socket[F],
    readTimeout:  FiniteDuration,
    writeTimeout: FiniteDuration,
    sslOptions:   SSLNegotiation.Options[F],
  )(
    implicit ev: MonadError[F, Throwable]
  ): Resource[F, Socket[F]] = {

    def fail[A](msg: String): F[A] =
      ev.raiseError(new Exception(s"Fatal failure during SSL negotiation: $msg"))

    val initiate: F[Byte] =
      socket.write(SSLRequest, Some(writeTimeout)) *>
      socket.read(1, Some(readTimeout)).map(_.flatMap(_.get(0))).flatMap {
        case None    => fail(s"EOF before 1 byte could be read.")
        case Some(b) => b.pure[F]
      }

    Resource.liftF(initiate).flatMap {
      case 'S' => sslOptions.tlsContext.client(socket, sslOptions.tlsParameters, sslOptions.logger)
      case 'N' => if (sslOptions.fallbackOk) socket.pure[Resource[F, *]] else Resource.liftF(fail(s"SSL not available."))
      case  c  => Resource.liftF(fail(s"SSL negotiation returned '$c', expected 'S' or 'N'."))
    }

  }


}