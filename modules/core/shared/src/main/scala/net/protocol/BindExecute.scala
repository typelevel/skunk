// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.effect.Resource
import cats.syntax.all._
import cats.effect.Concurrent
import skunk.~
import skunk.exception._
import skunk.net.message.{ Bind => BindMessage, Execute => ExecuteMessage, Close => CloseMessage, _ }
import skunk.net.MessageSocket
import skunk.net.Protocol.PortalId
import skunk.util.{ Origin, Namer }
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{Span, Tracer}
import skunk.RedactionStrategy
import skunk.net.Protocol
import skunk.data.{ Completion, TransactionStatus }
import skunk.net.protocol.exchange
import cats.effect.kernel.Deferred
import org.typelevel.otel4s.metrics.Histogram

trait BindExecute[F[_]] {

  def command[A](
    statement:  Protocol.PreparedCommand[F, A],
    args:       A,
    argsOrigin: Origin,
    redactionStrategy: RedactionStrategy
  ): Resource[F, Protocol.CommandPortal[F, A]]

  def query[A, B](
    statement:  Protocol.PreparedQuery[F, A, B],
    args:       A,
    argsOrigin: Origin,
    redactionStrategy: RedactionStrategy,
    initialSize: Int
  ): Resource[F, Protocol.QueryPortal[F, A, B]]

  def executeSized[A, B](
    statement:  Protocol.PreparedQuery[F, A, B],
    args:       A,
    argsOrigin: Origin,
    redactionStrategy: RedactionStrategy,
    maxRows: Int
  ): F[List[B] ~ Boolean]
}

object BindExecute {
  
  def apply[F[_]: Exchange: MessageSocket: Namer: Tracer](opDuration: Histogram[F, Double])(
    implicit ev: Concurrent[F]
  ): BindExecute[F] =
    new Unroll[F] with BindExecute[F] {
      
      /** @param syncSent whether the caller has already sent Sync. If so the backend discards to it
        *   and emits one ReadyForQuery, so the error path must consume that rather than send a second
        *   Sync.
        */
      def bindExchange[A](
        statement: Protocol.PreparedStatement[F, A],
        args: A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy,
        syncSent: Boolean
      ):(Span[F] => F[PortalId], F[Unit]) = {
        val ea  = statement.statement.encoder.encode(args) // encoded args
        
        def preBind(span: Span[F]): F[PortalId] = for {
              pn <- nextName("portal").map(PortalId(_))
              _  <- span.addAttributes(
                Attribute("arguments", redactionStrategy.redactArguments(ea).map(_.orNull).mkString(",")),
                Attribute("portal-id", pn.value)
              )
              _  <- send(BindMessage(pn.value, statement.id.value, ea.map(_.map(_.value))))
        } yield pn

        val postBind: F[Unit] = flatExpect {
          case BindComplete        => ().pure[F]
          case ErrorResponse(info) =>
            for {
              hi <- history(Int.MaxValue)
              _  <- send(Sync).unlessA(syncSent)
              _  <- expect { case ReadyForQuery(_) => }
              a  <- PostgresErrorException.raiseError[F, Unit](
                      sql             = statement.statement.sql,
                      sqlOrigin       = Some(statement.statement.origin),
                      info            = info,
                      history         = hi,
                      arguments       = statement.statement.encoder.types.zip(ea),
                      argumentsOrigin = Some(argsOrigin)
                    )
            } yield a
        }
        (preBind, postBind)
      }

      def command[A](
        statement:  Protocol.PreparedCommand[F, A],
        args:       A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy
      ): Resource[F, Protocol.CommandPortal[F, A]] = {

        val (preBind, postBind) = bindExchange(statement, args, argsOrigin, redactionStrategy, syncSent = true)

        val postExec: F[(Completion, TransactionStatus)] = flatExpect {
          // Sync went out with Bind and Execute, so ReadyForQuery is already on its way. Issue 210
          // requires that Sync be sent, not that it be sent here.
          // https://github.com/tpolecat/skunk/issues/210
          //
          // Keep the transaction status: it says whether the portal still exists.
          case CommandComplete(c) => expect { case ReadyForQuery(s) => (c, s) }

          case EmptyQueryResponse =>
            expect { case ReadyForQuery(_) => } *>
            new EmptyStatementException(statement.command).raiseError[F, (Completion, TransactionStatus)]

          // The backend performs the whole copy inside its handling of Execute, so it reaches our
          // Sync only afterwards and replies ReadyForQuery, which has to be consumed.
          case CopyOutResponse(_) =>
            receive.iterateUntil {
              case CommandComplete(_) => true
              case _                  => false
            } *>
            expect { case ReadyForQuery(_) => } *>
            new CopyNotSupportedException(statement.command).raiseError[F, (Completion, TransactionStatus)]

          // The backend ignores Flush and Sync in copy-in mode, so ours was swallowed and this
          // branch has to send its own.
          case CopyInResponse(_) =>
            send(CopyFail) *>
            expect { case ErrorResponse(_) => } *>
            send(Sync) *>
            expect { case ReadyForQuery(_) => } *>
            new CopyNotSupportedException(statement.command).raiseError[F, (Completion, TransactionStatus)]

          case ErrorResponse(info) =>
            for {
              hi <- history(Int.MaxValue)
              _  <- expect { case ReadyForQuery(_) => }
              redactedArgs = statement.command.encoder.types.zip(
                redactionStrategy.redactArguments(statement.command.encoder.encode(args)))
              a  <- new PostgresErrorException(
                      sql             = statement.command.sql,
                      sqlOrigin       = Some(statement.command.origin),
                      info            = info,
                      history         = hi,
                      arguments       = redactedArgs,
                      argumentsOrigin = Some(argsOrigin)
                    ).raiseError[F, (Completion, TransactionStatus)]
            } yield a
        }

        Resource.make {
          exchange("bind+execute", opDuration){ (span: Span[F]) =>
            for {
              pn <- preBind(span)
              _  <- send(ExecuteMessage(pn.value, 0))
              // Sync rather than Flush: a command never pages, so there is no portal to keep alive
              // between Executes, and all three replies come back from one flush.
              _  <- send(Sync)
              _  <- postBind
              ca <- postExec
            } yield {
              val (c, xa) = ca
              (new Protocol.CommandPortal[F, A](pn, statement, args, argsOrigin) {
                def execute: F[Completion] = c.pure
              }, xa)
            }
          }
        } { case (portal, xa) =>
          // Sync ended the implicit transaction and took the portal with it, so outside an explicit
          // transaction there is nothing to close. Inside one the portal lives until COMMIT and must
          // be closed or portals accumulate.
          //
          // Close cannot be folded into the write above: the backend ignores Flush and Sync during
          // copy-in but errors on anything else, and a command is only known to be COPY FROM STDIN
          // once CopyInResponse has been read.
          Close[F](opDuration).apply(portal.id).whenA(xa =!= TransactionStatus.Idle)
        } .map(_._1)

      }

      def executeSized[A, B](
        statement:  Protocol.PreparedQuery[F, A, B],
        args:       A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy,
        maxRows: Int
      ): F[List[B] ~ Boolean] = {

        // Sync goes out with Bind and Execute, as for a command. Safe here and not in `query`
        // because this path fetches once, so no second Execute can be stranded.
        val (preBind, postBind) = bindExchange(statement, args, argsOrigin, redactionStrategy, syncSent = true)

        // A span name distinct from `command` and `query`, which share "bind+execute".
        val fetch: F[(List[B] ~ Boolean, PortalId, TransactionStatus)] =
          exchange("bind+execute+sync", opDuration){ (span: Span[F]) =>
            for {
              pn <- preBind(span)
              _  <- span.addAttributes(
                      Attribute("max-rows",  maxRows.toLong),
                      Attribute("portal-id", pn.value)
                    )
              _  <- send(ExecuteMessage(pn.value, maxRows))
              _  <- send(Sync)
              _  <- postBind
              // Leaves the ReadyForQuery unread on success -- see unrollPresynced -- since we need
              // the status it carries. It must be read on the PortalSuspended path too, taken
              // whenever maxRows is reached, or the reply is left for the next operation.
              rs <- unrollPresynced(statement, args, argsOrigin, redactionStrategy)
                      // A decode failure is raised here, so the Close below never runs. A server
                      // error would have dropped the portal itself; a decode failure leaves the
                      // server happy and, inside a transaction, the portal open. The status is not
                      // known at this point, so close unconditionally -- legal either way. Inline,
                      // because the mutex is not reentrant, and attempted so that failing to close
                      // cannot mask the decode error.
                      .onError { case _: DecodeException[_, _, _] =>
                        (send(CloseMessage.portal(pn.value)) *>
                         send(Flush)                        *>
                         expect { case CloseComplete => }).attempt.void
                      }
              xa <- expect { case ReadyForQuery(s) => s }
            } yield (rs, pn, xa)
          }

        // Uncancelable across both exchanges, not just each one: `exchange` is individually
        // uncancelable but the join between them is a cancellation point, and being cancelled there
        // would skip the Close. Errors take the same route, which is why the decode path above
        // closes the portal itself.
        ev.uncancelable { _ =>
          fetch.flatMap { case (rs, pn, xa) =>
            // As in `command`: nothing to close outside an explicit transaction. Outside the
            // exchange above, because Close opens its own and the mutex is not reentrant.
            Close[F](opDuration).apply(pn).whenA(xa =!= TransactionStatus.Idle).as(rs)
          }
        }

      }

      def query[A, B](
        statement:  Protocol.PreparedQuery[F, A, B],
        args:       A,
        argsOrigin: Origin,
        redactionStrategy: RedactionStrategy,
        initialSize: Int
      ): Resource[F, Protocol.QueryPortal[F, A, B]] = {
        // Queries keep Flush: the portal must stay open for further Executes, which Sync would
        // prevent by ending the transaction that owns it.
        val (preBind, postBind) = bindExchange(statement, args, argsOrigin, redactionStrategy, syncSent = false)
        Resource.eval(Deferred[F, Unit]).flatMap { prefetch =>
          Resource.make {
            exchange("bind+execute", opDuration){ (span: Span[F]) =>
              for {
                pn <- preBind(span)
                _  <- span.addAttributes(
                        Attribute("max-rows",  initialSize.toLong),
                        Attribute("portal-id", pn.value)
                      )
                _  <- send(ExecuteMessage(pn.value, initialSize))
                _  <- send(Flush)
                _  <- postBind
                rs <- unroll(statement, args, argsOrigin, redactionStrategy)
              } yield new Protocol.QueryPortal[F, A, B](pn, statement, args, argsOrigin, redactionStrategy) {
                def execute(maxRows: Int): F[List[B] ~ Boolean] = 
                  prefetch.tryGet.flatMap {
                    case None => rs.pure <* prefetch.complete(())
                    case Some(()) => Execute[F](opDuration).apply(this, maxRows)
                  }
              }
            }
          } { portal => Close[F](opDuration).apply(portal.id)}
        }
      }
  }


}
