package skunk.net.protocol

import cats._
import cats.implicits._

import skunk._
import skunk.data.Completion
import skunk.util._
import cats._
import cats.implicits._
import skunk.net.message.{ Execute => ExecuteMessage, _ }
import skunk.net.{ Protocol, MessageSocket }

trait Execute[F[_]] {
  def apply[A](portal: Protocol.CommandPortal[F, A]): F[Completion]
  def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean]
}

object Execute {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Namer]: Execute[F] =
    new Unroll[F] with Execute[F] {

      def apply[A](portal: Protocol.CommandPortal[F, A]): F[Completion] =
        exchange {
          for {
            _  <- send(ExecuteMessage(portal.id.value, 0))
            _  <- send(Flush)
            c  <- expect {
              case CommandComplete(c) => c
              // TODO: we need the sql and arguments here
              // case ErrorResponse(e) =>
              //   for {
              //     _ <- expect { case ReadyForQuery(_) => }
              //     h <- history(Int.MaxValue)
              //     c <- Concurrent[F].raiseError[Completion](new PostgresErrorException(command.sql, None, e, h, Nil, None))
              //   } yield c
            }
          } yield c
        }

      def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean] =
        exchange {
          for {
            _  <- send(ExecuteMessage(portal.id.value, maxRows))
            _  <- send(Flush)
            rs <- unroll(portal)
          } yield rs
        }

    }

}