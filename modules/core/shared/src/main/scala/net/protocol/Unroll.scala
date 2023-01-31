// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.syntax.all._
import cats.MonadError
import skunk.{ ~, Encoder, Decoder }
import skunk.exception.DecodeException
import skunk.implicits._
import skunk.net.message._
import skunk.net.MessageSocket
import skunk.net.Protocol.QueryPortal
import skunk.util.Origin
import skunk.data.TypedRowDescription
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.AttributeKey
import org.typelevel.otel4s.trace.Tracer
import skunk.exception.PostgresErrorException
import scala.util.control.NonFatal

/**
 * Superclass for `Query` and `Execute` sub-protocols, both of which need a way to accumulate
 * results in a `List` and report errors when decoding fails.
 */
private[protocol] class Unroll[F[_]: MessageSocket: Tracer](
  implicit ev: MonadError[F, Throwable]
) {

  /** Receive the next batch of rows. */
  def unroll[A, B](
    portal:         QueryPortal[F, A, B]
  ): F[List[B] ~ Boolean] =
    unroll(
      extended       = true,
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
    extended:       Boolean,
    sql:            String,
    sqlOrigin:      Origin,
    args:           A,
    argsOrigin:     Option[Origin],
    encoder:        Encoder[A],
    rowDescription: TypedRowDescription,
    decoder:        Decoder[B],
  ): F[List[B] ~ Boolean] = {

    def syncAndFail(info: Map[Char, String]): F[List[List[Option[String]]] ~ Boolean]  =
      history(Int.MaxValue).flatMap { hi =>
        send(Sync).whenA(extended)          *>
        expect { case ReadyForQuery(_) => } *>
        new PostgresErrorException(
          sql             = sql,
          sqlOrigin       = Some(sqlOrigin),
          info            = info,
          history         = hi,
          arguments       = encoder.types.zip(encoder.encode(args)),
          argumentsOrigin = argsOrigin
        ).raiseError[F, List[List[Option[String]]] ~ Boolean]
      }

    def sync: F[Unit] =
      (send(Sync) >> expect { case ReadyForQuery(_) => }).whenA(extended)

    // N.B. we process all waiting messages to ensure the protocol isn't messed up by decoding
    // failures later on.
    def accumulate(accum: List[List[Option[String]]]): F[List[List[Option[String]]] ~ Boolean] =
      flatExpect {
        case rd @ RowData(_)          => accumulate(rd.fields :: accum)
        case      CommandComplete(_)  => sync.as((accum.reverse ~ false))
        case      PortalSuspended     => (accum.reverse ~ true).pure[F]
        case      ErrorResponse(info) => syncAndFail(info)
      }

    val rows: F[List[List[Option[String]]] ~ Boolean] =
      Tracer[F].span("read").use { span =>
        accumulate(Nil).flatTap { case (rows, bool) =>
          span.addAttributes(
            Attribute(AttributeKey.long("row-count"), rows.length.toLong),
            Attribute(AttributeKey.boolean("more-rows"), bool)
          )
        }
      }

    rows.flatMap { case (rows, bool) =>
      Tracer[F].span("decode").surround {
        rows.traverse { data =>

          // https://github.com/tpolecat/skunk/issues/129
          // an exception thrown in a decoder will derail things here, so let's handle it.
          val result =
            try decoder.decode(0, data)
            catch {
              case NonFatal(e) => Left(Decoder.Error(0, 0, e.getClass.getName, Some(e)))
            }

          result match {
            case Right(a) => a.pure[F]
            case Left(e)  =>
              send(Sync).whenA(extended) *> // if it's an extended query we need to resync
              expect { case ReadyForQuery(_) => } *>
              new DecodeException(
                data,
                e,
                sql,
                Some(sqlOrigin),
                args,
                argsOrigin,
                encoder,
                rowDescription,
                e.cause,
              ).raiseError[F, B]
          }
        } .map(a => a ~ bool)
      }

    }

  }
}
