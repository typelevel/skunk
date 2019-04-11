// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import skunk._
import skunk.implicits._
import skunk.util._
import skunk.data._
import skunk.exception._
import skunk.net.message.{ Query => QueryMessage, _ }
import cats._
import cats.effect.{Concurrent, Resource}
import cats.effect.implicits._
import cats.effect.concurrent.Semaphore
import cats.implicits._

/**
 * Atomic interactions with the database that consist of multiple message exchanges. These are
 * run under a mutex and are uninterruptable.
 */
trait Atomic[F[_]] {

  def startup(user: String, database: String): F[Unit]

  /**
   * Parse a statement, yielding the database id of a statement. Note that this resource must
   * be closed.
   */
  def parse[A](statement: Statement[A]): Resource[F, Protocol.StatementId]

  /** Bind a statement to arguments, yielding the database id of a portal. */
  def bind[A](
    statement:  Protocol.PreparedStatement[F, A],
    args:       A,
    argsOrigin: Origin
  ): Resource[F, Protocol.PortalId]

  def executeCommand(portalName: Protocol.PortalId): F[Completion]

  def executeQuickCommand(command: Command[Void]): F[Completion]

  def executeQuery[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean]

  def executeQuickQuery[B](query: Query[Void, B]): F[List[B]]

  def checkCommand(cmd: Command[_], stmt: Protocol.StatementId): F[Unit]

  def checkQuery[A](query: Query[_, A], stmt: Protocol.StatementId): F[RowDescription]

}



object Atomic {

  def fromBufferedMessageSocket[F[_]: Concurrent](ams: BufferedMessageSocket[F]): F[Atomic[F]] =
    for {
      nam <- Namer[F]
      sem <- Semaphore[F](1)
    } yield new Atomic[F] {

      def startup(user: String, database: String): F[Unit] =
        atomically {
          for {
            _ <- ams.send(StartupMessage(user, database))
            _ <- ams.expect { case AuthenticationOk => }
            _ <- ams.expect { case ReadyForQuery(_) => }
          } yield ()
        }

      /** Parse a statement, yielding [the name of] a statement, which will be closed after use. */
      def parse[A](statement: Statement[A]): Resource[F, Protocol.StatementId] =
        Resource.make {
          atomically {
            for {
              n <- nam.nextName("statement").map(Protocol.StatementId)
              _ <- ams.send(Parse(n.value, statement.sql, statement.encoder.types.toList))
              _ <- ams.send(Flush)
              _ <- ams.flatExpect {
                case ParseComplete    => ().pure[F]
                case ErrorResponse(e) =>
                  for {
                    h <- ams.history(Int.MaxValue)
                    a <- resyncAndRaise[Unit] {
                      new PostgresErrorException(
                        sql       = statement.sql,
                        sqlOrigin = statement.origin,
                        info      = e,
                        history   = h,
                      )
                    }
                  } yield a
              }
            } yield n
          }
        } { name => close(Close.statement(name.value)) }

      /**
       * Bind a statement to arguments, yielding [the name of] a portal, which will be closed after
       * use.
       */
      def bind[A](
        statement:  Protocol.PreparedStatement[F, A],
        args:       A,
        argsOrigin: Origin
      ): Resource[F, Protocol.PortalId] =
        Resource.make {
          atomically {
            for {
              pn <- nam.nextName("portal").map(Protocol.PortalId)
              _  <- ams.send(Bind(pn.value, statement.id.value, statement.statement.encoder.encode(args)))
              _  <- ams.send(Flush)
              _  <- ams.flatExpect {
                case BindComplete     => ().pure[F]
                case ErrorResponse(info) =>
                  for {
                    h <- ams.history(Int.MaxValue)
                    a <- resyncAndRaise[Unit](new PostgresErrorException(
                      sql             = statement.statement.sql,
                      sqlOrigin       = statement.statement.origin,
                      info            = info,
                      history         = h,
                      arguments       = statement.statement.encoder.types.zip(statement.statement.encoder.encode(args)),
                      argumentsOrigin = Some(argsOrigin)
                    ))
                  } yield a
              }
            } yield pn
          }
        } { name => close(Close.portal(name.value)) }

      def executeCommand(portalName: Protocol.PortalId): F[Completion] =
        atomically {
          for {
            _  <- ams.send(Execute(portalName.value, 0))
            _  <- ams.send(Flush)
            c  <- ams.expect {
              case CommandComplete(c) => c
              // TODO: we need the sql and arguments here
              // case ErrorResponse(e) =>
              //   for {
              //     _ <- ams.expect { case ReadyForQuery(_) => }
              //     h <- ams.history(Int.MaxValue)
              //     c <- Concurrent[F].raiseError[Completion](new PostgresErrorException(command.sql, None, e, h, Nil, None))
              //   } yield c
            }
          } yield c
        }

      def executeQuickCommand(command: Command[Void]): F[Completion] =
        atomically {
          ams.send(QueryMessage(command.sql)) *> ams.flatExpect {
            case CommandComplete(c) => ams.expect { case ReadyForQuery(_) => c }
            // TODO: case RowDescription => oops, this returns rows, it needs to be a query
            case ErrorResponse(e) =>
              for {
                _ <- ams.expect { case ReadyForQuery(_) => }
                h <- ams.history(Int.MaxValue)
                c <- Concurrent[F].raiseError[Completion](new PostgresErrorException(command.sql, None, e, h, Nil, None))
              } yield c
          }
        }

      def executeQuery[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean] =
        atomically {
          for {
            _  <- ams.send(Execute(portal.id.value, maxRows))
            _  <- ams.send(Flush)
            rs <- unroll(portal)
          } yield rs
        }

      def executeQuickQuery[B](query: Query[Void, B]): F[List[B]] =
        atomically {
          ams.send(QueryMessage(query.sql)) *> ams.flatExpect {

            case rd @ RowDescription(_) =>
              val ok =  query.decoder.types.map(_.oid) === rd.oids
              for {
                _  <- Concurrent[F].raiseError(ColumnAlignmentException(query, rd)).unlessA(ok)
                rs <- unroll(
                        sql            = query.sql,
                        sqlOrigin      = query.origin,
                        args           = Void,
                        argsOrigin     = None,
                        encoder        = Void.codec,
                        rowDescription = rd,
                        decoder        = query.decoder,
                    ).map(_._1) // rs._2 will always be true here
                _  <- ams.expect { case ReadyForQuery(_) => }
              } yield rs

            case NoData                 =>
              Concurrent[F].raiseError[List[B]](NoDataException(query))

            case ErrorResponse(e) =>
              for {
                _  <- ams.expect { case ReadyForQuery(_) => }
                h  <- ams.history(Int.MaxValue)
                rs <- Concurrent[F].raiseError[List[B]](new PostgresErrorException(query.sql, query.origin, e, h, Nil, None))
              } yield rs
            }
        }

      def checkCommand(cmd: Command[_], stmt: Protocol.StatementId): F[Unit] =
        atomically {
          for {
            _  <- ams.send(Describe.statement(stmt.value))
            _  <- ams.send(Flush)
            _  <- ams.expect { case ParameterDescription(_) => } // always ok
            _  <- ams.flatExpect {
                    case NoData                 => ().pure[F]
                    case rd @ RowDescription(_) =>
                      Concurrent[F].raiseError[Unit](UnexpectedRowsException(cmd, rd))
                  }
          } yield ()
        }

      def checkQuery[A](query: Query[_, A], stmt: Protocol.StatementId): F[RowDescription] =
        atomically {
          for {
            _  <- ams.send(Describe.statement(stmt.value))
            _  <- ams.send(Flush)
            _  <- ams.expect { case ParameterDescription(_) => } // always ok
            rd <- ams.flatExpect {
                    case rd @ RowDescription(_) => rd.pure[F]
                    case NoData                 =>
                      Concurrent[F].raiseError[RowDescription](NoDataException(query))
                  }
            ok =  query.decoder.types.map(_.oid) === rd.oids
            _  <- Concurrent[F].raiseError(ColumnAlignmentException(query, rd)).unlessA(ok)
          } yield rd
        }

      ///
      /// HELPERS
      ///

      def atomically[A](fa: F[A]): F[A] =
        sem.withPermit(fa).uncancelable

      /** Receive the next batch of rows. */
      def unroll[A, B](
        portal: Protocol.QueryPortal[F, A, B]
      ): F[List[B] ~ Boolean] =
        unroll(
          sql            = portal.preparedQuery.query.sql,
          sqlOrigin      = portal.preparedQuery.query.origin,
          args           = portal.arguments,
          argsOrigin     = Some(portal.argumentsOrigin),
          encoder        = portal.preparedQuery.query.encoder,
          rowDescription = portal.preparedQuery.rowDescription,
          decoder        = portal.preparedQuery.query.decoder
        )

      // When we do a quick query there's no statement to hang onto all the error-reporting context
      // so we have to pass everything in manually.
      def unroll[A, B](
        sql:            String,
        sqlOrigin:      Option[Origin],
        args:           A,
        argsOrigin:     Option[Origin],
        encoder:        Encoder[A],
        rowDescription: RowDescription,
        decoder:        Decoder[B]
      ): F[List[B] ~ Boolean] = {

        // N.B. we process all waiting messages to ensure the protocol isn't messed up by decoding
        // failures later on.
        def accumulate(accum: List[List[Option[String]]]): F[List[List[Option[String]]] ~ Boolean] =
          ams.receive.flatMap {
            case rd @ RowData(_)         => accumulate(rd.fields :: accum)
            case      CommandComplete(_) => (accum.reverse ~ false).pure[F]
            case      PortalSuspended    => (accum.reverse ~ true).pure[F]
          }

        accumulate(Nil).flatMap {
          case (rows, bool) =>
            rows.traverse { data =>
              decoder.decode(0, data) match {
                case Right(a) => a.pure[F]
                case Left(e)  => Concurrent[F].raiseError[B](new DecodeException(
                  data,
                  e,
                  sql,
                  sqlOrigin,
                  args,
                  argsOrigin,
                  encoder,
                  rowDescription
                ))
              }
            } .map(_ ~ bool)
        }

      }

      def close(message: Close): F[Unit] =
        atomically {
          for {
            _ <- ams.send(message)
            _ <- ams.send(Flush)
            _ <- ams.expect { case CloseComplete => }
          } yield ()
        }

      /** Re-sync after an error to get the session back to a usable state, then raise the error. */
      def resyncAndRaise[A](t: Throwable): F[A] =
        for {
          _ <- ams.send(Sync)
          _ <- ams.expect { case ReadyForQuery(_) => }
          a <- ApplicativeError[F, Throwable].raiseError[A](t)
        } yield a


    }

}