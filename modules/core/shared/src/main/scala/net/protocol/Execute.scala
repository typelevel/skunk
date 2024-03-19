// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.syntax.all._
import cats.MonadError
import skunk.~
import skunk.net.{ Protocol, MessageSocket }
import skunk.net.message.{ Execute => ExecuteMessage, _ }
import skunk.util.Typer
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer

trait Execute[F[_]] {
  def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int, ty: Typer): F[List[B] ~ Boolean]
}

object Execute {

  def apply[F[_]: Exchange: MessageSocket: Tracer](
    implicit ev: MonadError[F, Throwable]
  ): Execute[F] =
    new Unroll[F] with Execute[F] {

      override def apply[A, B](portal: Protocol.QueryPortal[F, A, B], maxRows: Int, ty: Typer): F[List[B] ~ Boolean] =
        exchange("execute") { (span: Span[F]) =>
          for {
            _  <- span.addAttributes(
                    Attribute("max-rows",    maxRows.toLong),
                    Attribute("portal-id", portal.id.value)
                  )
            _  <- send(ExecuteMessage(portal.id.value, maxRows))
            _  <- send(Flush)
            rs <- unroll(portal)
          } yield rs
        }

    }

}
