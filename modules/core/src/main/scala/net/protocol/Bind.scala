package skunk.net.protocol

import cats._
import cats.effect.Resource
import cats.implicits._

import skunk.util._
import skunk.exception._
import cats._
import cats.implicits._
import skunk.net.Protocol
import skunk.net.message.{ Bind => BindMessage, Close => CloseMessage, _ }
import skunk.net.MessageSocket

trait Bind[F[_]] {

  def apply[A](
    statement:  Protocol.PreparedStatement[F, A],
    args:       A,
    argsOrigin: Origin
  ): Resource[F, Protocol.PortalId]

}

object Bind {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Namer]: Bind[F] =
    new Bind[F] {

      def apply[A](
        statement:  Protocol.PreparedStatement[F, A],
        args:       A,
        argsOrigin: Origin
      ): Resource[F, Protocol.PortalId] =
        Resource.make {
          exchange {
            for {
              pn <- nextName("portal").map(Protocol.PortalId)
              _  <- send(BindMessage(pn.value, statement.id.value, statement.statement.encoder.encode(args)))
              _  <- send(Flush)
              _  <- flatExpect {
                      case BindComplete        => ().pure[F]
                      case ErrorResponse(info) => syncAndFail(statement,  args, argsOrigin, info)
                    }
            } yield pn
          }
        } { name => Close[F].apply(CloseMessage.portal(name.value)) }

      def syncAndFail[A](
        statement: Protocol.PreparedStatement[F, A],
        args:       A,
        argsOrigin: Origin,
        info: Map[Char, String]
      ): F[Unit] =
        for {
          hi <- history(Int.MaxValue)
          _  <- send(Sync)
          _  <- expect { case ReadyForQuery(_) => }
          a  <- new PostgresErrorException(
                  sql             = statement.statement.sql,
                  sqlOrigin       = Some(statement.statement.origin),
                  info            = info,
                  history         = hi,
                  arguments       = statement.statement.encoder.types.zip(statement.statement.encoder.encode(args)),
                  argumentsOrigin = Some(argsOrigin)
                ).raiseError[F, Unit]
        } yield a

    }

}