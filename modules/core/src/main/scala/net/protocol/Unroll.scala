// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.implicits._
import cats.MonadError
import skunk.{ ~, Encoder, Decoder }
import skunk.exception.DecodeException
import skunk.implicits._
import skunk.net.message._
import skunk.net.MessageSocket
import skunk.net.Protocol.QueryPortal
import skunk.util.Origin
import skunk.data.TypedRowDescription
import natchez.Trace

/**
 * Superclass for `Query` and `Execute` sub-protocols, both of which need a way to accumulate
 * results in a `List` and report errors when decoding fails.
 */
private[protocol] class Unroll[F[_]: MonadError[?[_], Throwable]: MessageSocket: Trace] {

  /** Receive the next batch of rows. */
  def unroll[A, B](
    portal:         QueryPortal[F, A, B]
  ): F[List[B] ~ Boolean] =
    unroll(
      sql            = portal.preparedQuery.query.sql,
      sqlOrigin      = portal.preparedQuery.query.origin,
      args           = portal.arguments,
      argsOrigin     = Some(portal.argumentsOrigin),
      encoder        = portal.preparedQuery.query.encoder,
      rowDescription = portal.preparedQuery.rowDescription,
      decoder        = portal.preparedQuery.query.decoder,
    )

  // When we do a quick query there's no statement to hang onto all the error-reporting context
  // so we have to pass everything in manually.
  def unroll[A, B](
    sql:            String,
    sqlOrigin:      Origin,
    args:           A,
    argsOrigin:     Option[Origin],
    encoder:        Encoder[A],
    rowDescription: TypedRowDescription,
    decoder:        Decoder[B],
  ): F[List[B] ~ Boolean] = {

    // N.B. we process all waiting messages to ensure the protocol isn't messed up by decoding
    // failures later on.
    def accumulate(accum: List[List[Option[String]]]): F[List[List[Option[String]]] ~ Boolean] =
      receive.flatMap {
        case rd @ RowData(_)         => accumulate(rd.fields :: accum)
        case      CommandComplete(_) => (accum.reverse ~ false).pure[F]
        case      PortalSuspended    => (accum.reverse ~ true).pure[F]
      }

    accumulate(Nil).flatMap {
      case (rows, bool) =>
        Trace[F].put(
          "row-count" -> rows.length,
          "more-rows" -> bool
        ) *>
        rows.traverse { data =>
          decoder.decode(0, data) match {
            case Right(a) => a.pure[F]
            case Left(e)  =>
              // if the portal is suspended we need to sync back up
              (send(Sync) *> expect { case ReadyForQuery(_) => }).whenA(bool) *>
              new DecodeException(
                data,
                e,
                sql,
                Some(sqlOrigin),
                args,
                argsOrigin,
                encoder,
                rowDescription,
              ).raiseError[F, B]
          }
        } .map(_ ~ bool)
    }

  }

}
