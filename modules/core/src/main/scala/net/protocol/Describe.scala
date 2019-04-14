package skunk.net.protocol

import cats.MonadError
import cats.implicits._
import skunk.exception.{ UnexpectedRowsException, ColumnAlignmentException, NoDataException }
import skunk.net.MessageSocket
import skunk.net.Protocol.StatementId
import skunk.net.message.{ Describe => DescribeMessage, _ }

trait Describe[F[_]] {
  def apply(cmd: skunk.Command[_], id: StatementId): F[Unit]
  def apply[A](query: skunk.Query[_, A], id: StatementId): F[RowDescription]
}

object Describe {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket]: Describe[F] =
    new Describe[F] {

      def apply(cmd: skunk.Command[_], id: StatementId): F[Unit] =
        exchange {
          for {
            _  <- send(DescribeMessage.statement(id.value))
            _  <- send(Flush)
            _  <- expect { case ParameterDescription(_) => } // always ok
            _  <- flatExpect {
                    case NoData                 => ().pure[F]
                    case rd @ RowDescription(_) => UnexpectedRowsException(cmd, rd).raiseError[F, Unit]
                  }
          } yield ()
        }

      def apply[A](query: skunk.Query[_, A], id: StatementId): F[RowDescription] =
        exchange {
          for {
            _  <- send(DescribeMessage.statement(id.value))
            _  <- send(Flush)
            _  <- expect { case ParameterDescription(_) => } // always ok
            rd <- flatExpect {
                    case rd @ RowDescription(_) => rd.pure[F]
                    case NoData                 => NoDataException(query).raiseError[F, RowDescription]
                  }
            ok =  query.decoder.types.map(_.oid) === rd.oids
            _  <- ColumnAlignmentException(query, rd).raiseError[F, Unit].unlessA(ok)
          } yield rd
        }

    }

}