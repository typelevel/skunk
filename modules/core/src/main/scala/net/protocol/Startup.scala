// Copyright (c) 2018-2020 by Rob Norris
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
  UnsupportedAuthenticationSchemeException,
  UnsupportedSASLMechanismsException
}
import com.ongres.scram.client.ScramClient
import com.ongres.scram.common.stringprep.StringPreparations

trait Startup[F[_]] {
  def apply(user: String, database: String, password: Option[String]): F[Unit]
}

object Startup {

  def apply[F[_]: MonadError[*[_], Throwable]: Exchange: MessageSocket: Trace]: Startup[F] =
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
            _ <- flatExpectStartup(sm) {
                    case AuthenticationOk                => ().pure[F]
                    case AuthenticationMD5Password(salt) => authenticationMD5Password[F](sm, password, salt)
                    case AuthenticationSASL(mechanisms)  => authenticationSASL[F](sm, password, mechanisms)
                    case m @ (
                      AuthenticationCleartextPassword |
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
    private def authenticationMD5Password[F[_]: MonadError[*[_], Throwable]: Exchange: MessageSocket: Trace](
      sm:       StartupMessage,
      password: Option[String],
      salt:     Array[Byte]
    ): F[Unit] =
      Trace[F].span("authenticationMD5Password") {
        requirePassword[F](sm, password).flatMap { pw =>
          for {
            _ <- send(PasswordMessage.md5(sm.user, pw, salt))
            _ <- flatExpectStartup(sm) { case AuthenticationOk => ().pure[F] }
          } yield ()
        }
      }

    private def authenticationSASL[F[_]: MonadError[*[_], Throwable]: Exchange: MessageSocket: Trace](
      sm:         StartupMessage,
      password:   Option[String],
      mechanisms: List[String]
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

    private def requirePassword[F[_]: ApplicativeError[*[_], Throwable]](sm: StartupMessage, password: Option[String]): F[String] =
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

    private def flatExpectStartup[F[_], B](sm: StartupMessage)(f: PartialFunction[BackendMessage, F[B]])(
      implicit ev: MessageSocket[F],
               or: Origin,
               F: ApplicativeError[F, Throwable]
    ): F[B] = flatExpect(f orElse {
      case ErrorResponse(info) =>
        new StartupException(info, sm.properties).raiseError[F, B]
    })

    private def guardScramAction[F[_]: ApplicativeError[*[_], Throwable], A](f: => A): F[A] =
      try f.pure[F]
      catch { case NonFatal(t) => 
        new SCRAMProtocolException(t.getMessage).raiseError[F, A]
      }

    private def bytesUtf8(value: String): ByteVector =
      ByteVector.view(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
}
