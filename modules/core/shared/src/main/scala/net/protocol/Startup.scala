// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.{ApplicativeError, MonadError, MonadThrow}
import cats.syntax.all._
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer
import scala.util.control.NonFatal
import scodec.bits.ByteVector
import skunk.net.MessageSocket
import skunk.net.message._
import skunk.util.Origin
import skunk.exception.{
  SCRAMProtocolException,
  StartupException,
  SkunkException,
  UnsupportedAuthenticationSchemeException,
  UnsupportedSASLMechanismsException
}
import org.typelevel.otel4s.metrics.Histogram
import cats.effect.MonadCancel

trait Startup[F[_]] {
  def apply(user: String, database: String, password: Option[String], parameters: Map[String, String]): F[Unit]
}

object Startup {

  def apply[F[_]: Exchange: MessageSocket: Tracer](opDuration: Histogram[F, Double])(
    implicit ev: MonadCancel[F, Throwable]
  ): Startup[F] =
    new Startup[F] {
      override def apply(user: String, database: String, password: Option[String], parameters: Map[String, String]): F[Unit] =
        exchange("startup", opDuration) { (span: Span[F]) =>
          val sm = StartupMessage(user, database, parameters)
          for {
            _ <- span.addAttributes(
              Attribute("user", user),
              Attribute("database", database)
            )
            _ <- send(sm)
            _ <- flatExpectStartup(sm) {
                    case AuthenticationOk                => ().pure[F]
                    case AuthenticationCleartextPassword => authenticationCleartextPassword[F](sm, password)
                    case AuthenticationMD5Password(salt) => authenticationMD5Password[F](sm, password, salt)
                    case AuthenticationSASL(mechanisms)  => authenticationSASL[F](sm, password, mechanisms)
                    case m @ (
                      AuthenticationGSS |
                      AuthenticationKerberosV5 |
                      AuthenticationSASL(_) |
                      AuthenticationSCMCredential |
                      AuthenticationSSPI )               => new UnsupportedAuthenticationSchemeException(m).raiseError[F, Unit]
                 }
            _ <- flatExpectStartup(sm) {
                   case ReadyForQuery(_) => ().pure[F]
                 }
          } yield ()
        }
    }

  // already inside an exchange
  private def authenticationCleartextPassword[F[_]: MessageSocket: Tracer](
    sm:       StartupMessage,
    password: Option[String]
  )(
    implicit ev: MonadError[F, Throwable]
  ): F[Unit] =
    Tracer[F].span("authenticationCleartextPassword").surround {
      requirePassword[F](sm, password).flatMap { pw =>
        for {
          _ <- send(PasswordMessage.cleartext(pw))
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
      }
    }

  private def authenticationMD5Password[F[_]: MessageSocket: Tracer](
    sm:       StartupMessage,
    password: Option[String],
    salt:     Array[Byte]
  )(
    implicit ev: MonadError[F, Throwable]
  ): F[Unit] =
    Tracer[F].span("authenticationMD5Password").surround {
      requirePassword[F](sm, password).flatMap { pw =>
        for {
          _ <- send(PasswordMessage.md5(sm.user, pw, salt))
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
      }
    }

  private def authenticationSASL[F[_]: MonadThrow: MessageSocket: Tracer](
    sm:         StartupMessage,
    password:   Option[String],
    mechanisms: List[String]
  ): F[Unit] =
    Tracer[F].span("authenticationSASL").surround {
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

  private[protocol] def requirePassword[F[_]](sm: StartupMessage, password: Option[String])(
    implicit ev: ApplicativeError[F, Throwable]
  ): F[String] =
    password match {
      case Some(pw) => pw.pure[F]
      case None =>
        new SkunkException(
          sql     = None,
          message = "Password required.",
          detail  = Some(s"The PostgreSQL server requested a password for '${sm.user}' but none was given."),
          hint    = Some("Specify a password when constructing your Session or Session pool.")
        ).raiseError[F, String]
  }

  private[protocol] def flatExpectStartup[F[_], B](sm: StartupMessage)(f: PartialFunction[BackendMessage, F[B]])(
    implicit ev: MessageSocket[F],
              or: Origin,
              F: ApplicativeError[F, Throwable]
  ): F[B] = flatExpect(f orElse {
    case ErrorResponse(info) =>
      new StartupException(info, sm.properties).raiseError[F, B]
  })

  private[protocol] def guardScramAction[F[_], A](f: => A)(
    implicit ev: ApplicativeError[F, Throwable]
  ): F[A] =
    try f.pure[F]
    catch { case NonFatal(t) =>
      new SCRAMProtocolException(t.getMessage).raiseError[F, A]
    }

  private[protocol] def bytesUtf8(value: String): ByteVector =
    ByteVector.view(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
}
