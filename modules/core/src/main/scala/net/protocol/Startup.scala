// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.MonadError
import cats.syntax.all._
import skunk.net.MessageSocket
import skunk.net.message._
import skunk.exception.StartupException
import natchez.Trace
import skunk.exception.SkunkException
import skunk.exception.UnsupportedAuthenticationSchemeException

trait Startup[F[_]] {
  def apply(user: String, database: String, password: Option[String]): F[Unit]
}

object Startup {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Trace]: Startup[F] =
    new Startup[F] {
      override def apply(user: String, database: String, password: Option[String]): F[Unit] =
        exchange("startup") {
          val sm = StartupMessage(user, database)
          for {
            _ <- Trace[F].put(
                   "user"     -> user,
                   "database" -> database
                 )
            _ <- send(sm)
            _ <- flatExpect {
                    case AuthenticationOk                => ().pure[F]
                    case AuthenticationMD5Password(salt) => authenticationMD5Password[F](sm, password, salt)
                    case m @ (
                      AuthenticationCleartextPassword |
                      AuthenticationGSS |
                      AuthenticationKerberosV5 |
                      AuthenticationSASL(_) |
                      AuthenticationSCMCredential |
                      AuthenticationSSPI )               => new UnsupportedAuthenticationSchemeException(m).raiseError[F, Unit]
                    case ErrorResponse(info) =>
                      val e = new StartupException(info, sm.properties)
                      e.raiseError[F, Unit]
                 }
            _ <- flatExpect {
                   case ReadyForQuery(_) => ().pure[F]
                   case ErrorResponse(info) =>
                    val e = new StartupException(info, sm.properties)
                    e.raiseError[F, Unit]
                 }
          } yield ()
        }
    }

    // already inside an exchange
    private def authenticationMD5Password[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Trace](
      sm:       StartupMessage,
      password: Option[String],
      salt:     Array[Byte]
    ): F[Unit] =
      Trace[F].span("authenticationMD5Password") {
        password match {

          case Some(pw) =>
            for {
              _ <- send(PasswordMessage.md5(sm.user, pw, salt))
              _ <- flatExpect {
                    case AuthenticationOk => ().pure[F]
                    case ErrorResponse(info) => new StartupException(info, sm.properties).raiseError[F, Unit]
                  }
            } yield ()

          case None     =>
            new SkunkException(
              sql     = None,
              message = "Password required.",
              detail  = Some(s"The PostgreSQL server requested a password for '${sm.user}' but none was given."),
              hint    = Some("Specify a password when constructing your Session or Session pool.")
            ).raiseError[F, Unit]

        }
      }

}