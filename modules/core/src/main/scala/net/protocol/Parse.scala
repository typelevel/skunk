package skunk.net.protocol

import cats.effect.Resource
import cats.implicits._
import cats.MonadError
import skunk.exception.PostgresErrorException
import skunk.net.message.{ Parse => ParseMessage, Close => CloseMessage, _ }
import skunk.net.MessageSocket
import skunk.net.Protocol.StatementId
import skunk.Statement
import skunk.util.Namer

trait Parse[F[_]] {
  def apply[A](statement: Statement[A]): Resource[F, StatementId]
}

object Parse {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Namer]: Parse[F] =
    new Parse[F] {

      def apply[A](statement: Statement[A]): Resource[F, StatementId] =
        Resource.make {
          exchange {
            for {
              id <- nextName("statement").map(StatementId)
              _  <- send(ParseMessage(id.value, statement.sql, statement.encoder.types.toList))
              _  <- send(Flush)
              _  <- flatExpect {
                      case ParseComplete       => ().pure[F]
                      case ErrorResponse(info) => syncAndFail(statement, info)
                    }
            } yield id
          }
        } { id => Close[F].apply(CloseMessage.statement(id.value)) }

      def syncAndFail(statement: Statement[_], info: Map[Char, String]): F[Unit] =
        for {
          hi <- history(Int.MaxValue)
          _  <- send(Sync)
          _  <- expect { case ReadyForQuery(_) => }
          a  <- new PostgresErrorException(
                  sql       = statement.sql,
                  sqlOrigin = Some(statement.origin),
                  info      = info,
                  history   = hi,
                ).raiseError[F, Unit]
        } yield a

    }

}