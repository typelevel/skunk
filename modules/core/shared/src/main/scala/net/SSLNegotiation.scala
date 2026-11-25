// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import cats._
import cats.effect._
import cats.syntax.all._
import fs2.Chunk
import fs2.io.net.Socket
import fs2.io.net.tls.TLSContext
import fs2.io.net.tls.TLSParameters
import fs2.io.net.tls.TLSLogger
import com.comcast.ip4s.Host
import com.comcast.ip4s.Hostname

object SSLNegotiation extends SSLNegotiationPlatform {

  /** Parameters for `negotiateSSL`. */
  case class Options[F[_]](
    tlsContext:    TLSContext[F],
    tlsParameters: TLSParameters,
    fallbackOk:    Boolean,
    logger:        Option[String => F[Unit]],
  )

  /**
   * If `options` still carries the default `TLSParameters`, which name no server, replace them with
   * defaults naming `host`. On Scala Native and Scala.js the TLS client needs a server name to verify
   * the certificate against (without one, s2n rejects every certificate), and on every platform it is
   * what gets sent as SNI. Explicitly configured parameters, and IP-address hosts, are left as they are.
   */
  def withServerName[F[_]](options: Options[F], host: Host): Options[F] =
    host match {
      case h: Hostname if options.tlsParameters == TLSParameters.Default =>
        options.copy(tlsParameters = serverNameParameters(h.toString))
      case _ => options
    }

  // SSLRequest message, as a Chunk[Byte]
  val SSLRequest: Chunk[Byte] =
    Chunk.array(Array[Byte](0, 0, 0, 8, 4, -46, 22, 47))

  /**
   * Negotiate SSL with Postgres, given a brand new connected `Socket` and a `TLSContext`. If SSL is
   * unavailable, fall back to the unencrypted socket if `fallbackOk`, otherwise raise an exception.
   */
  def negotiateSSL[F[_]](
    socket:       Socket[F],
    sslOptions:   SSLNegotiation.Options[F]
  )(
    implicit ev: MonadError[F, Throwable]
  ): Resource[F, Socket[F]] = {

    def fail[A](msg: String): F[A] =
      ev.raiseError(new Exception(s"Fatal failure during SSL negotiation: $msg"))

    val initiate: F[Byte] =
      socket.write(SSLRequest) *>
      socket.read(1).map(_.flatMap(_.get(0))).flatMap {
        case None    => fail(s"EOF before 1 byte could be read.")
        case Some(b) => b.pure[F]
      }

    Resource.eval(initiate).flatMap {
      case 'S' => 
        sslOptions.tlsContext.clientBuilder(socket).withParameters(sslOptions.tlsParameters).withLogger(
          sslOptions.logger.fold[TLSLogger[F]](TLSLogger.Disabled)(logger => TLSLogger.Enabled(x => logger(x)))
        ).build
      case 'N' => if (sslOptions.fallbackOk) socket.pure[Resource[F, *]] else Resource.eval(fail(s"SSL not available."))
      case  c  => Resource.eval(fail(s"SSL negotiation returned '$c', expected 'S' or 'N'."))
    }

  }


}
