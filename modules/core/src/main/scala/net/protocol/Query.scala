// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.MonadError
import cats.implicits._
import skunk.{ Command, Void }
import skunk.data.Completion
import skunk.exception.{ ColumnAlignmentException, NoDataException, PostgresErrorException }
import skunk.net.message.{ Query => QueryMessage, _ }
import skunk.net.MessageSocket
import skunk.util.Typer
import skunk.exception.UnknownOidException
import natchez.Trace

trait Query[F[_]] {
  def apply(command: Command[Void]): F[Completion]
  def apply[B](query: skunk.Query[Void, B], ty: Typer): F[List[B]]
}

object Query {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket: Trace]: Query[F] =
    new Unroll[F] with Query[F] {

      override def apply[B](query: skunk.Query[Void, B], ty: Typer): F[List[B]] =
        exchange("query") {
          Trace[F].put(
            "query.sql" -> query.sql
          ) *> send(QueryMessage(query.sql)) *> flatExpect {

            // If we get a RowDescription back it means we have a valid query as far as Postgres is
            // concerned, and we will soon receive zero or more RowData followed by CommandComplete.
            case rd @ RowDescription(_) =>

              // If our decoder lines up with the RowDescription we can decode the rows, otherwise
              // we have to discard them and then raise an error. All the args are necessary context
              // if we have a decoding failure and need to report an error.
              rd.typed(ty) match {

                case Right(td) =>
                  if (query.decoder.types === td.types) {
                    unroll(
                      extended       = false,
                      sql            = query.sql,
                      sqlOrigin      = query.origin,
                      args           = Void,
                      argsOrigin     = None,
                      encoder        = Void.codec,
                      rowDescription = td,
                      decoder        = query.decoder,
                    ).map(_._1) <* expect { case ReadyForQuery(_) => }
                  } else {
                    discard *> ColumnAlignmentException(query, td).raiseError[F, List[B]]
                  }

                case Left(err) =>
                  discard *> UnknownOidException(query, err).raiseError[F, List[B]]

              }

            // If we get CommandComplete it means our Query was actually a Command. Postgres doesn't
            // distinguish these but we do, so this is an error.
            case CommandComplete(_) =>
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

            // We can get a warning if this was actually a command and something wasn't quite
            // right. In this case we'll report the first error because it's probably more
            // informative.
            case NoticeResponse(e) =>
              for {
                hi <- history(Int.MaxValue)
                _  <- expect { case CommandComplete(_) => }
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

      override def apply(command: Command[Void]): F[Completion] =
        exchange("query") {
          Trace[F].put(
            "command.sql" -> command.sql
          ) *> send(QueryMessage(command.sql)) *> flatExpect {

            case CommandComplete(c) =>
              for {
                _ <- expect { case ReadyForQuery(_) => }
              } yield c

            case ErrorResponse(e) =>
              for {
                _ <- expect { case ReadyForQuery(_) => }
                h <- history(Int.MaxValue)
                c <- new PostgresErrorException(command.sql, Some(command.origin), e, h, Nil, None).raiseError[F, Completion]
              } yield c

            case NoticeResponse(e) =>
              for {
                _ <- expect { case CommandComplete(_) => }
                _ <- expect { case ReadyForQuery(_) =>  }
                h <- history(Int.MaxValue)
                c <- new PostgresErrorException(command.sql, Some(command.origin), e, h, Nil, None).raiseError[F, Completion]
              } yield c

            // TODO: case RowDescription => oops, this returns rows, it needs to be a query

          }
        }

      // If there is an error we just want to receive and discard everything until we have seen
      // CommandComplete followed by ReadyForQuery.
      val discard: F[Unit] =
        receive.flatMap {
          case RowData(_)         => discard
          case CommandComplete(_) => expect { case ReadyForQuery(_) => }
        }


    }

}