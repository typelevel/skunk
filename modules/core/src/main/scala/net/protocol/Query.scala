package skunk.net.protocol

import cats.MonadError
import cats.implicits._
import skunk.{ Command, Void }
import skunk.data.Completion
import skunk.exception.{ ColumnAlignmentException, NoDataException, PostgresErrorException }
import skunk.net.message.{ Query => QueryMessage, _ }
import skunk.net.MessageSocket

trait Query[F[_]] {
  def apply(command: Command[Void]): F[Completion]
  def apply[B](query: skunk.Query[Void, B]): F[List[B]]
}

object Query {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket]: Query[F] =
    new Unroll[F] with Query[F] {

      def apply[B](query: skunk.Query[Void, B]): F[List[B]] =
        exchange {
          send(QueryMessage(query.sql)) *> flatExpect {

            // If we get a RowDescription back it means we have a valid query as far as Postgres is
            // concerned, and we will soon receive zero or more RowData followed by CommandComplete.
            case rd @ RowDescription(_) =>

              // If our decoder lines up with the RowDescription we can decode the rows, otherwise
              // we have to discard them and then raise an error. All the args are necessary context
              // if we have a decoding failure and need to report an error.
              if (query.decoder.types.map(_.oid) === rd.oids) {
                unroll(
                  sql            = query.sql,
                  sqlOrigin      = query.origin,
                  args           = Void,
                  argsOrigin     = None,
                  encoder        = Void.codec,
                  rowDescription = rd,
                  decoder        = query.decoder,
                ).map(_._1) <* expect { case ReadyForQuery(_) => }
              } else {
                discard *> ColumnAlignmentException(query, rd).raiseError[F, List[B]]
              }

            // If we get CommandComplete it means our Query was actually a Command. Postgres doesn't
            // distinguish these but we do, so this is an error.
            case CommandComplete(completion) =>
              expect { case ReadyForQuery(_) => } *> NoDataException(query).raiseError[F, List[B]]

            // If we get an ErrorResponse it means there was an error in the query. In this case we
            // simply await ReadyForQuery and then raise an error.
            case ErrorResponse(e) =>
              for {
                hi <- history(Int.MaxValue)
                _  <- expect { case ReadyForQuery(_) => }
                rs <- (new PostgresErrorException(
                        sql       = query.sql,
                        sqlOrigin = Some(query.origin),
                        info      = e,
                        history   = hi,
                      )).raiseError[F, List[B]]
              } yield rs
            }

        }

        def apply(command: Command[Void]): F[Completion] =
          exchange {
            send(QueryMessage(command.sql)) *> flatExpect {
              case CommandComplete(c) => expect { case ReadyForQuery(_) => c }
              // TODO: case RowDescription => oops, this returns rows, it needs to be a query
              case ErrorResponse(e) =>
                for {
                  _ <- expect { case ReadyForQuery(_) => }
                  h <- history(Int.MaxValue)
                  c <- new PostgresErrorException(command.sql, None, e, h, Nil, None).raiseError[F, Completion]
                } yield c
            }
          }

        // If there is an error we just want to receive and discard everything until we have seen
        // CommandComplete followed by ReadyForQuery.
        val discard: F[Unit] =
          receive.flatMap {
            case rd @ RowData(_)         => discard
            case      CommandComplete(_) => expect { case ReadyForQuery(_) => }
          }


    }

}