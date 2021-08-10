// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import com.ongres.scram.client.ScramClient
import com.ongres.scram.common.stringprep.StringPreparations

import cats.MonadError
import cats.syntax.all._
import natchez.Trace
import scala.util.control.NonFatal
import skunk.net.MessageSocket
import skunk.net.message._
import skunk.exception.{
  SCRAMProtocolException,
  UnsupportedSASLMechanismsException
}

private[protocol] trait StartupCompanionPlatform { this: Startup.type =>
  
  private[protocol] def authenticationSASL[F[_]: MessageSocket: Trace](
    sm:         StartupMessage,
    password:   Option[String],
    mechanisms: List[String]
  )(
    implicit ev: MonadError[F, Throwable]
  ): F[Unit] =
    Trace[F].span("authenticationSASL") {
        for {
          client <- {
            try ScramClient.
              channelBinding(ScramClient.ChannelBinding.NO).
              stringPreparation(StringPreparations.SASL_PREPARATION).
              selectMechanismBasedOnServerAdvertised(mechanisms.toArray: _*).
              setup().pure[F]
            catch {
              case _: IllegalArgumentException => new UnsupportedSASLMechanismsException(mechanisms).raiseError[F, ScramClient]
              case NonFatal(t) => new SCRAMProtocolException(t.getMessage).raiseError[F, ScramClient]
            }
          }
          session = client.scramSession("*")
          _ <- send(SASLInitialResponse(client.getScramMechanism.getName, bytesUtf8(session.clientFirstMessage)))
          serverFirstBytes <- flatExpectStartup(sm) {
            case AuthenticationSASLContinue(serverFirstBytes) => serverFirstBytes.pure[F]
          }
          serverFirst <- guardScramAction {
            session.receiveServerFirstMessage(new String(serverFirstBytes.toArray, "UTF-8"))
          }
          pw <- requirePassword[F](sm, password)
          clientFinal = serverFirst.clientFinalProcessor(pw)
          _ <- send(SASLResponse(bytesUtf8(clientFinal.clientFinalMessage)))
          serverFinalBytes <- flatExpectStartup(sm) {
            case AuthenticationSASLFinal(serverFinalBytes) => serverFinalBytes.pure[F]
          }
          _ <- guardScramAction {
            clientFinal.receiveServerFinalMessage(new String(serverFinalBytes.toArray, "UTF-8")).pure[F]
          }
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
    }

}
