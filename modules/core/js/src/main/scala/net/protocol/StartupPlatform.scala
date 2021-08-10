// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.MonadThrow
import cats.syntax.all._
import natchez.Trace
import skunk.net.MessageSocket
import skunk.net.message._
import skunk.exception.{ 
  SCRAMProtocolException,
  UnsupportedSASLMechanismsException
}

private[protocol] trait StartupCompanionPlatform { this: Startup.type =>
  
  private[protocol] def authenticationSASL[F[_]: MonadThrow: MessageSocket: Trace](
    sm:         StartupMessage,
    password:   Option[String],
    mechanisms: List[String]
  ): F[Unit] =
    Trace[F].span("authenticationSASL") {
      if (mechanisms.contains(Scram.SaslMechanism)) {
        for {
          pw <- requirePassword[F](sm, password)
          channelBinding = Scram.NoChannelBinding
          clientFirstBare = Scram.clientFirstBareWithRandomNonce
          _ <- send(Scram.saslInitialResponse(channelBinding, clientFirstBare))
          serverFirstBytes <- flatExpectStartup(sm) {
            case AuthenticationSASLContinue(serverFirstBytes) => serverFirstBytes.pure[F]
          }
          serverFirst <- Scram.ServerFirst.decode(serverFirstBytes) match {
            case Some(serverFirst) => serverFirst.pure[F]
            case None =>
              new SCRAMProtocolException(
                s"Failed to parse server-first-message in SASLInitialResponse: ${serverFirstBytes.toHex}."
              ).raiseError[F, Scram.ServerFirst]
          }
          (response, expectedVerifier) = Scram.saslChallenge(pw, channelBinding, serverFirst, clientFirstBare, serverFirstBytes)
          _ <- send(response)
          serverFinalBytes <- flatExpectStartup(sm) {
            case AuthenticationSASLFinal(serverFinalBytes) => serverFinalBytes.pure[F]
          }
          _ <- Scram.ServerFinal.decode(serverFinalBytes) match {
            case Some(serverFinal) =>
              if (serverFinal.verifier == expectedVerifier) ().pure[F]
              else new SCRAMProtocolException(
                s"Expected verifier ${expectedVerifier.value.toHex} but received ${serverFinal.verifier.value.toHex}."
              ).raiseError[F, Unit]
            case None =>
              new SCRAMProtocolException(
                s"Failed to parse server-final-message in AuthenticationSASLFinal: ${serverFinalBytes.toHex}."
              ).raiseError[F, Unit]
          }
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
      } else {
        new UnsupportedSASLMechanismsException(mechanisms).raiseError[F, Unit]
      }
    }

}
