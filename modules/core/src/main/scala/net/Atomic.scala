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
import cats.effect.Concurrent
import cats.effect.implicits._
import cats.effect.concurrent.Semaphore
import cats.implicits._

/**
 * Atomic interactions with the database that consist of multiple message exchanges. These are
 * run under a mutex and are uninterruptable.
 */
class Atomic[F[_]: Concurrent](
  ams:   BufferedMessageSocket[F],
  nam:   Namer[F],
  sem:   Semaphore[F],
) {

  // It's possible to break the protocol via concurrency and cancellation so let's ensure that
  // any protocol step executes in its entirety. An exception here is `execute` which needs a
  // special cancellation handler to connect on another socket and actually attempt server-side
  // cancellation (todo). Another possibility would be to do guaranteeCase and try to resyncAndRaise by
  // re-syncing and rolling back if necessary. but there are a lot of cases to consider. It's
  // simpler to treat protocol exchanges as atomic.
  private def atomically[A](fa: F[A]): F[A] =
    sem.withPermit(fa).uncancelable

  // TODO: authentication
  // TODO: good error message
  def startup(user: String, database: String): F[Unit] =
    atomically {
      for {
        _ <- ams.send(StartupMessage(user, database))
        _ <- ams.expect { case AuthenticationOk => }
        _ <- ams.expect { case ReadyForQuery(_) => }
      } yield ()
    }

  private def close(message: Close): F[Unit] =
    atomically {
      for {
        _ <- ams.send(message)
        _ <- ams.send(Flush)
        _ <- ams.expect { case CloseComplete => }
      } yield ()
    }

  def closePortal(name: String): F[Unit] =
    close(Close.portal(name))

  def closeStmt(name: String): F[Unit] =
    close(Close.statement(name))

  /** Re-sync after an error to get the session back to a usable state, then raise the error. */
  private def resyncAndRaise[A](t: Throwable): F[A] =
    for {
      _ <- ams.send(Sync)
      _ <- ams.expect { case ReadyForQuery(_) => }
      a <- ApplicativeError[F, Throwable].raiseError[A](t)
    } yield a

  /** Parse a statement, yielding [the name of] a statement. */
  def parse[A](
    sql:       String,
    sqlOrigin: Option[Origin],
    enc:       Encoder[A]
  ): F[String] =
    atomically {
      for {
        n <- nam.nextName("statement")
        _ <- ams.send(Parse(n, sql, enc.types.toList))
        _ <- ams.send(Flush)
        _ <- ams.flatExpect {
          case ParseComplete    => ().pure[F]
          case ErrorResponse(e) =>
            for {
              h <- ams.history(Int.MaxValue)
              a <- resyncAndRaise[Unit] {
                new PostgresErrorException(
                  sql       = sql,
                  sqlOrigin = sqlOrigin,
                  info      = e,
                  history   = h,
                )
              }
            } yield a
        }
      } yield n
    }

  /** Bind a statement to arguments, yielding [the name of] a portal. */
  def bind[A](
    sql:        String,
    sqlOrigin:  Option[Origin],
    stmt:       String,
    enc:        Encoder[A],
    args:       A,
    argsOrigin: Origin
  ): F[String] =
    atomically {
      for {
        pn <- nam.nextName("portal")
        _  <- ams.send(Bind(pn, stmt, enc.encode(args)))
        _  <- ams.send(Flush)
        _  <- ams.flatExpect {
          case BindComplete     => ().pure[F]
          case ErrorResponse(info) =>
            for {
              h <- ams.history(Int.MaxValue)
              a <- resyncAndRaise[Unit](new PostgresErrorException(
                sql             = sql,
                sqlOrigin       = sqlOrigin,
                info            = info,
                history         = h,
                arguments       = enc.types.zip(enc.encode(args)),
                argumentsOrigin = Some(argsOrigin)
              ))
            } yield a
        }
      } yield pn
    }

  def executeCommand(portalName: String): F[Completion] =
    atomically {
      for {
        _  <- ams.send(Execute(portalName, 0))
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

  def executeQuery[B](query: Query[_, B], portal: String, maxRows: Int): F[List[B] ~ Boolean] =
    atomically {
      for {
        _  <- ams.send(Execute(portal, maxRows))
        _  <- ams.send(Flush)
        rs <- unroll(query.decoder)
      } yield rs
    }

  def executeQuickQuery[B](query: Query[Void, B]): F[List[B]] =
    atomically {
      ams.send(QueryMessage(query.sql)) *> ams.flatExpect {

        case rd @ RowDescription(_) =>
          for {
            // _  <- printStatement(query.sql).whenA(check)
            // _  <- checkRowDescription(rd, query.decoder).whenA(check)
            rs <- unroll(query.decoder).map(_._1) // rs._2 will always be true here
            _  <- ams.expect { case ReadyForQuery(_) => }
          } yield rs

        case ErrorResponse(e) =>
          for {
            _  <- ams.expect { case ReadyForQuery(_) => }
            h  <- ams.history(Int.MaxValue)
            rs <- Concurrent[F].raiseError[List[B]](new PostgresErrorException(query.sql, query.origin, e, h, Nil, None))
          } yield rs
        }

    }

  def checkCommand(cmd: Command[_], stmt: String): F[Unit] =
    atomically {
      for {
        _  <- ams.send(Describe.statement(stmt))
        _  <- ams.send(Flush)
        _  <- ams.expect { case ParameterDescription(_) => } // always ok
        _  <- ams.flatExpect {
                case NoData                 => ().pure[F]
                case rd @ RowDescription(_) => Concurrent[F].raiseError[Unit](UnexpectedRowsException(cmd, rd))
              }
      } yield ()
    }

  // Two things can go wrong here.
  def checkQuery[A](query: Query[_, A], stmt: String): F[Unit] =
    atomically {
      for {
        _  <- ams.send(Describe.statement(stmt))
        _  <- ams.send(Flush)
        _  <- ams.expect { case ParameterDescription(_) => } // always ok
        rd <- ams.flatExpect {
                case rd @ RowDescription(_) => rd.pure[F]
                case NoData              => Concurrent[F].raiseError[RowDescription](NoDataException(query)) // this isn't a query!
              }
        ok =  query.decoder.types.map(_.oid) === rd.oids
        _  <- Concurrent[F].raiseError(ColumnAlignmentException(query, rd)).unlessA(ok)
      } yield ()
    }

  /** Receive the next batch of rows. */
  private def unroll[A](dec: Decoder[A]): F[List[A] ~ Boolean] = {
    // Accumulate all the data first, then map it to the result type. This ensures that any decoding
    // errors won't mess up the protocol.
    def go(accum: List[List[Option[String]]]): F[List[List[Option[String]]] ~ Boolean] =
      ams.receive.flatMap {
        case rd @ RowData(_)         => go(rd.fields :: accum) // TODO: when we encounter null here we need a good diagnostic report
        case      CommandComplete(_) => (accum.reverse ~ false).pure[F]
        case      PortalSuspended    => (accum.reverse ~ true).pure[F]
      }
    go(Nil).map {
      case (data, bool) => (data.map(dec.decode), bool)
    }
  }

}
