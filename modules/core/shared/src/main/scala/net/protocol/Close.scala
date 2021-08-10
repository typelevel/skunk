// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net
package protocol

import cats.FlatMap
import cats.syntax.all._
import skunk.net.message.{ Close => CloseMessage, Flush, CloseComplete }
import skunk.net.MessageSocket
import natchez.Trace

trait Close[F[_]] {
  def apply(portalId: Protocol.PortalId): F[Unit]
  def apply(statementId: Protocol.StatementId): F[Unit]
}

object Close {

  def apply[F[_]: FlatMap: Exchange: MessageSocket: Trace]: Close[F] =
    new Close[F] {

      override def apply(portalId: Protocol.PortalId): F[Unit] =
        exchange("close-portal") {
          Trace[F].put("portal" -> portalId.value) *>
          close(CloseMessage.portal(portalId.value))
        }

      override def apply(statementId: Protocol.StatementId): F[Unit] =
        exchange("close-statement") {
          Trace[F].put("statement" -> statementId.value) *>
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