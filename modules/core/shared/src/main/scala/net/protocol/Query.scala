// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.MonadError
import cats.syntax.all._
import skunk.{ Command, Void, RedactionStrategy }
import skunk.data.Completion
import skunk.exception._
import skunk.net.message.{ Query => QueryMessage, _ }
import skunk.net.MessageSocket
import skunk.util.Typer
import org.typelevel.otel4s.semconv.attributes.DbAttributes
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer
import skunk.Statement

trait Query[F[_]] {
  def apply(command: Command[Void]): F[Completion]
  def apply[B](query: skunk.Query[Void, B], ty: Typer): F[List[B]]
  def applyDiscard(statement: Statement[Void]): F[Unit]
}

object Query {

  def apply[F[_]: Exchange: MessageSocket: Tracer](redactionStrategy: RedactionStrategy)(
    implicit ev: MonadError[F, Throwable]
  ): Query[F] =
    new Unroll[F] with Query[F] {

      def finishCopyOut: F[Unit] =
        receive.iterateUntil {
          case CommandComplete(_) => true
          case _                  => false
        } .void

      def finishUp(stmt: Statement[_], multipleStatements: Boolean = false): F[Unit] =
        flatExpect {

          case ReadyForQuery(_) =>
            new SkunkException(
              message   = "Multi-statement queries and commands are not supported.",
              hint      = Some("Break up semicolon-separated statements and execute them independently."),
              sql       = Some(stmt.sql),
              sqlOrigin = Some(stmt.origin),
            ).raiseError[F, Unit].whenA(multipleStatements)

          case RowDescription(_) | RowData(_) | CommandComplete(_) | ErrorResponse(_) | EmptyQueryResponse =>
            finishUp(stmt, true)

          case CopyInResponse(_) =>
            send(CopyFail)                      *>
            expect { case ErrorResponse(_) => } *>
            finishUp(stmt, true)

          case CopyOutResponse(_) =>
            finishCopyOut *>
            finishUp(stmt, true)

        }

      // If there is an error we just want to receive and discard everything until we have seen
      // CommandComplete followed by ReadyForQuery.
      def discard(stmt: Statement[_]): F[Unit] =
        flatExpect {
          case RowData(_)         => discard(stmt)
          case CommandComplete(_) => finishUp(stmt)
        }

      override def apply[B](query: skunk.Query[Void, B], ty: Typer): F[List[B]] =
        exchange("query") { (span: Span[F]) =>
          span.addAttribute(
            DbAttributes.DbQueryText(query.sql)
          ) *> send(QueryMessage(query.sql)) *> flatExpect {

            // If we get a RowDescription back it means we have a valid query as far as Postgres is
            // concerned, and we will soon receive zero or more RowData followed by CommandComplete.
            case rd @ RowDescription(_) =>

              // If our decoder lines up with the RowDescription we can decode the rows, otherwise
              // we have to discard them and then raise an error. All the args are necessary context
              // if we have a decoding failure and need to report an error.
              rd.typed(ty) match {

                case Right(td) =>
                  if (query.isDynamic || query.decoder.types === td.types) {
                    unroll(
                      extended       = false,
                      sql            = query.sql,
                      sqlOrigin      = query.origin,
                      args           = Void,
                      argsOrigin     = None,
                      encoder        = Void.codec,
                      rowDescription = td,
                      decoder        = query.decoder,
                      redactionStrategy = redactionStrategy
                    ).map(_._1) <* finishUp(query)
                  } else {
                    discard(query) *> ColumnAlignmentException(query, td).raiseError[F, List[B]]
                  }

                case Left(err) =>
                  discard(query) *> UnknownOidException(query, err, ty.strategy).raiseError[F, List[B]]

              }

            // We don't support COPY FROM STDIN yet but we do need to be able to clean up if a user
            // tries it.
            case CopyInResponse(_) =>
              send(CopyFail)  *>
              expect { case ErrorResponse(_) => } *>
              finishUp(query) *>
              new CopyNotSupportedException(query).raiseError[F, List[B]]

            case CopyOutResponse(_) =>
              finishCopyOut *>
              finishUp(query) *>
              new CopyNotSupportedException(query).raiseError[F, List[B]]

            // Query is empty, whitespace, all comments, etc.
            case EmptyQueryResponse =>
              finishUp(query) *> new EmptyStatementException(query).raiseError[F, List[B]]

            // If we get CommandComplete it means our Query was actually a Command. Postgres doesn't
            // distinguish these but we do, so this is an error.
            case CommandComplete(_) =>
              finishUp(query) *> NoDataException(query).raiseError[F, List[B]]

            // If we get an ErrorResponse it means there was an error in the query. In this case we
            // simply await ReadyForQuery and then raise an error.
            case ErrorResponse(e) =>
              for {
                hi <- history(Int.MaxValue)
                _  <- finishUp(query)
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
                _  <- finishUp(query)
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
        exchange("query") { (span: Span[F]) =>
          span.addAttribute(
            DbAttributes.DbQueryText(command.sql)
          ) *> send(QueryMessage(command.sql)) *> flatExpect {

            case CommandComplete(c) =>
              finishUp(command).as(c)

            case EmptyQueryResponse =>
              finishUp(command) *> new EmptyStatementException(command).raiseError[F, Completion]

            case ErrorResponse(e) =>
              for {
                _ <- finishUp(command)
                h <- history(Int.MaxValue)
                c <- new PostgresErrorException(command.sql, Some(command.origin), e, h, Nil, None).raiseError[F, Completion]
              } yield c

            case NoticeResponse(e) =>
              for {
                _ <- expect { case CommandComplete(_) => }
                _ <- finishUp(command)
                h <- history(Int.MaxValue)
                c <- new PostgresErrorException(command.sql, Some(command.origin), e, h, Nil, None).raiseError[F, Completion]
              } yield c

            // If we get rows back it means this should have been a query!
            case RowDescription(_) =>
              finishUp(command) *> UnexpectedDataException(command).raiseError[F, Completion]

            // We don't support COPY FROM STDIN yet but we do need to be able to clean up if a user
            // tries it.
            case CopyInResponse(_) =>
              send(CopyFail)  *>
              expect { case ErrorResponse(_) => } *>
              finishUp(command) *>
              new CopyNotSupportedException(command).raiseError[F, Completion]

            case CopyOutResponse(_) =>
              finishCopyOut *>
              finishUp(command) *>
              new CopyNotSupportedException(command).raiseError[F, Completion]

            }


        }

      // Finish up any single or multi-query statement, discard returned completions and/or rows
      // Fail with first encountered error
      def finishUpDiscard(stmt: Statement[_], error: Option[SkunkException]): F[Unit] =
        flatExpect {
          case ReadyForQuery(_) => error match {
            case None => ().pure[F]
            case Some(e) => e.raiseError[F, Unit]
          }

          case RowDescription(_) | RowData(_) | CommandComplete(_) | EmptyQueryResponse | NoticeResponse(_) =>
            finishUpDiscard(stmt, error)

          case ErrorResponse(info) =>
            error match {
              case None =>
                for {
                  hi <- history(Int.MaxValue) 
                  err = new PostgresErrorException(stmt.sql, Some(stmt.origin), info, hi)
                  c <- finishUpDiscard(stmt, Some(err))
                } yield c
              case _ => finishUpDiscard(stmt, error)
            }

          // We don't support COPY FROM STDIN yet but we do need to be able to clean up if a user
          // tries it.
          case CopyInResponse(_) =>
            send(CopyFail)                      *>
            expect { case ErrorResponse(_) => } *>
            finishUpDiscard(stmt, error.orElse(new CopyNotSupportedException(stmt).some))

          case CopyOutResponse(_) =>
            finishCopyOut *> finishUpDiscard(stmt, error.orElse(new CopyNotSupportedException(stmt).some))
        }

      override def applyDiscard(statement: Statement[Void]): F[Unit] =
        exchange("query") { (span: Span[F]) =>
          span.addAttribute(
            DbAttributes.DbQueryText(statement.sql)
          ) *> send(QueryMessage(statement.sql)) *> finishUpDiscard(statement, None)
        }
    }
}
