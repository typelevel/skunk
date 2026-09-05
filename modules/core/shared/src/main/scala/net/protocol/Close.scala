// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net
package protocol

import cats.effect.MonadCancelThrow
import cats.syntax.all._
import skunk.net.message.{ Close => CloseMessage, Flush, CloseComplete }
import skunk.telemetry.{SkunkAttributes, Telemetry}

trait Close[F[_]] {
  def apply(portalId: Protocol.PortalId): F[Unit]
  def apply(statementId: Protocol.StatementId): F[Unit]
}

object Close {

  def apply[F[_]: MonadCancelThrow: Exchange: MessageSocket: Telemetry]: Close[F] =
    new Close[F] {

      override def apply(portalId: Protocol.PortalId): F[Unit] =
        exchange("close-portal") {
          Telemetry[F].addProtocolAttributes(SkunkAttributes.portalId(portalId.value)) *>
          close(CloseMessage.portal(portalId.value))
        }

      override def apply(statementId: Protocol.StatementId): F[Unit] =
        exchange("close-statement") {
          Telemetry[F].addProtocolAttributes(SkunkAttributes.statementId(statementId.value)) *>
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
