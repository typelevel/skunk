// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net
package protocol

import cats.FlatMap
import cats.syntax.all._
import skunk.net.message.{ Close => CloseMessage, Flush, CloseComplete }
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer

trait Close[F[_]] {
  def apply(portalId: Protocol.PortalId): F[Unit]
  def apply(statementId: Protocol.StatementId): F[Unit]
}

object Close {

  def apply[F[_]: FlatMap: Exchange: MessageSocket: Tracer]: Close[F] =
    new AbstractClose[F] {
      protected def weakExchange[A](label: String)(f: Span[F] => F[A]) =
        exchange(label)(f)
    }

  /** Like [[apply]] but doesn't acquire a mutex, allowing usage from within an existing exchange. */
  private[skunk] def midExchange[F[_]: FlatMap: MessageSocket: Tracer]: Close[F] =
    new AbstractClose[F] {
      protected def weakExchange[A](label: String)(f: Span[F] => F[A]) =
        Tracer[F].span(label).use(f)
    }

  private abstract class AbstractClose[F[_]: FlatMap: MessageSocket] extends Close[F] {
    /** Ad-hoc abstraction of [[exchange]], allowing `midExchange` to be implemented. */
    protected def weakExchange[A](label: String)(f: Span[F] => F[A]): F[A]

    override def apply(portalId: Protocol.PortalId): F[Unit] =
      weakExchange("close-portal") { (span: Span[F]) =>
        span.addAttribute(Attribute("portal", portalId.value)) *>
        close(CloseMessage.portal(portalId.value))
      }

    override def apply(statementId: Protocol.StatementId): F[Unit] =
      weakExchange("close-statement") { (span: Span[F]) =>
        span.addAttribute(Attribute("statement", statementId.value)) *>
        close(CloseMessage.statement(statementId.value))
      }

    def close(message: CloseMessage): F[Unit] =
      for {
        _ <- send(message)
        _ <- send(Flush)
        _ <- expect { case CloseComplete => }
      } yield ()
  }

}
