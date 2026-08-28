// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.syntax.all._
import cats.effect.MonadCancel
import skunk.~
import skunk.net.{ Protocol, MessageSocket }
import skunk.net.message.{ Execute => ExecuteMessage, _ }
import skunk.telemetry.{SkunkAttributes, Telemetry}

trait Execute[F[_]] {
  def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean]
}

object Execute {

  def apply[F[_]: Exchange: MessageSocket: Telemetry](
    implicit ev: MonadCancel[F, Throwable]
  ): Execute[F] =
    new Unroll[F] with Execute[F] {

      override def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int): F[List[B] ~ Boolean] =
        database(
          "execute",
          portal.preparedStatement.statement,
          portal.preparedStatement.statement.encoder.encode(portal.arguments),
          portal.redactionStrategy,
        ) {
            for {
              _  <- Telemetry[F].addAttributes(
                      SkunkAttributes.fetchMaxRows(maxRows.toLong),
                      SkunkAttributes.portalId(portal.id.value),
                      SkunkAttributes.statementId(portal.preparedStatement.id.value)
                    )
              _  <- send(ExecuteMessage(portal.id.value, maxRows))
              _  <- send(Flush)
              rs <- unroll(portal)
            } yield rs
        }

    }

}
