package skunk.net.protocol

import cats._
import cats.implicits._
import skunk._
import skunk.exception._
import skunk.implicits._
import skunk.net.message._
import skunk.net.{ MessageSocket, Protocol }
import skunk.util.Origin

/**
 * Superclass for `Query` and `Execute` sub-protocols, both of which need a way to accumulate
 * results in a `List` and report errors when decoding fails.
 */
private[protocol] class Unroll[F[_]: MonadError[?[_], Throwable]: MessageSocket] {

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
    sqlOrigin:      Origin,
    args:           A,
    argsOrigin:     Option[Origin],
    encoder:        Encoder[A],
    rowDescription: RowDescription,
    decoder:        Decoder[B]
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
        rows.traverse { data =>
          decoder.decode(0, data) match {
            case Right(a) => a.pure[F]
            case Left(e)  =>
              // need to discard remaining rows!
              def discard: F[Unit] = receive.flatMap {
                case rd @ RowData(_)         => discard
                case      CommandComplete(_) | PortalSuspended    => expect { case ReadyForQuery(_) => }
                case ReadyForQuery(_) => ().pure[F]
              }

            discard *>
            (new DecodeException(
              data,
              e,
              sql,
              Some(sqlOrigin),
              args,
              argsOrigin,
              encoder,
              rowDescription
            )).raiseError[F, B]
          }
        } .map(_ ~ bool)
    }

  }

}
