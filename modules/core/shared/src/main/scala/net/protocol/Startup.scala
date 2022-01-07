// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.{ApplicativeError, MonadError}
import cats.syntax.all._
import natchez.Trace
import scala.util.control.NonFatal
import scodec.bits.ByteVector
import skunk.net.MessageSocket
import skunk.net.message._
import skunk.util.Origin
import skunk.exception.{
  SCRAMProtocolException,
  StartupException,
  SkunkException,
  UnsupportedAuthenticationSchemeException
}

trait Startup[F[_]] {
  def apply(user: String, database: String, password: Option[String], parameters: Map[String, String]): F[Unit]
}

object Startup extends StartupCompanionPlatform {

  def apply[F[_]: Exchange: MessageSocket: Trace](
    implicit ev: MonadError[F, Throwable]
  ): Startup[F] =
    new Startup[F] {
      override def apply(user: String, database: String, password: Option[String], parameters: Map[String, String]): F[Unit] =
        exchange("startup") {
          val sm = StartupMessage(user, database, parameters)
          for {
            _ <- Trace[F].put(
                   "user"     -> user,
                   "database" -> database
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
  private def authenticationCleartextPassword[F[_]: MessageSocket: Trace](
    sm:       StartupMessage,
    password: Option[String]
  )(
    implicit ev: MonadError[F, Throwable]
  ): F[Unit] =
    Trace[F].span("authenticationCleartextPassword") {
      requirePassword[F](sm, password).flatMap { pw =>
        for {
          _ <- send(PasswordMessage.cleartext(pw))
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
      }
    }

  private def authenticationMD5Password[F[_]: MessageSocket: Trace](
    sm:       StartupMessage,
    password: Option[String],
    salt:     Array[Byte]
  )(
    implicit ev: MonadError[F, Throwable]
  ): F[Unit] =
    Trace[F].span("authenticationMD5Password") {
      requirePassword[F](sm, password).flatMap { pw =>
        for {
          _ <- send(PasswordMessage.md5(sm.user, pw, salt))
          _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
        } yield ()
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
