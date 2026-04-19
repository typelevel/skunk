// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import com.ongres.scram.client.ScramClient
import com.ongres.scram.common.StringPreparation

import cats.MonadError
import cats.syntax.all._
import org.typelevel.otel4s.trace.Tracer
import scala.util.control.NonFatal
import skunk.net.MessageSocket
import skunk.net.message._
import skunk.exception.{
  SCRAMProtocolException,
  UnsupportedSASLMechanismsException
}

private[protocol] trait StartupCompanionPlatform { this: Startup.type =>

  private[protocol] def authenticationSASL[F[_]: MessageSocket: Tracer](
    sm:         StartupMessage,
    password:   Option[String],
    mechanisms: List[String]
  )(
    implicit ev: MonadError[F, Throwable]
  ): F[Unit] =
    Tracer[F].span("authenticationSASL").surround {
        import scala.jdk.CollectionConverters._

        for {
          pw <- requirePassword[F](sm, password)
          client <- {
            try ScramClient.builder().
              advertisedMechanisms(mechanisms.asJava).
              username(sm.user).
              password(pw.toCharArray).
              stringPreparation(StringPreparation.POSTGRESQL_PREPARATION).
              build().pure[F]
            catch {
              case _: IllegalArgumentException => new UnsupportedSASLMechanismsException(mechanisms).raiseError[F, ScramClient]
              case NonFatal(t) => new SCRAMProtocolException(t.getMessage).raiseError[F, ScramClient]
            }
          }
          _ <- send(SASLInitialResponse(client.getScramMechanism.getName, bytesUtf8(client.clientFirstMessage().toString)))
          serverFirstBytes <- flatExpectStartup(sm) {
            case AuthenticationSASLContinue(serverFirstBytes) => serverFirstBytes.pure[F]
          }
          _ <- guardScramAction {
            client.serverFirstMessage(new String(serverFirstBytes.toArray, "UTF-8")).pure[F]
          }
          _ <- send(SASLResponse(bytesUtf8(client.clientFinalMessage().toString)))
          serverFinalBytes <- flatExpectStartup(sm) {
            case AuthenticationSASLFinal(serverFinalBytes) => serverFinalBytes.pure[F]
          }
          _ <- guardScramAction {
            client.serverFinalMessage(new String(serverFinalBytes.toArray, "UTF-8")).pure[F]
          }
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
    }

}
