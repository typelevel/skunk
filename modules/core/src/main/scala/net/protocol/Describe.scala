// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats._
import cats.effect.Ref
import cats.syntax.all._
import skunk.exception.{ UnexpectedRowsException, ColumnAlignmentException, NoDataException }
import skunk.net.MessageSocket
import skunk.net.Protocol.StatementId
import skunk.net.message.{ Describe => DescribeMessage, _ }
import skunk.util.{ StatementCache, Typer }
import skunk.exception.UnknownOidException
import skunk.data.TypedRowDescription
import natchez.Trace
import cats.data.OptionT

trait Describe[F[_]] {
  def apply(cmd: skunk.Command[_], id: StatementId, ty: Typer): F[Unit]
  def apply[A](query: skunk.Query[_, A], id: StatementId, ty: Typer): F[TypedRowDescription]
}

object Describe {

  def apply[F[_]: Exchange: MessageSocket: Trace](cache: Cache[F])(
    implicit ev: MonadError[F, Throwable]
  ): Describe[F] =
    new Describe[F] {

      override def apply(cmd: skunk.Command[_], id: StatementId, ty: Typer): F[Unit] =
        exchange("describe") {
          OptionT(cache.commandCache.get(cmd)).getOrElseF {
            for {
              _  <- Trace[F].put("statement-id" -> id.value)
              _  <- send(DescribeMessage.statement(id.value))
              _  <- send(Flush)
              _  <- expect { case ParameterDescription(_) => } // always ok
              _  <- flatExpect {
                      case NoData                 => ().pure[F]
                      case rd @ RowDescription(_) =>
                        rd.typed(ty) match {
                          // We shouldn't have a row description at all, but if we can't decode its
                          // types then *that's* the error we will raise; only if we decode it
                          // successfully we will raise the underlying error. Seems a little confusing
                          // but it's a very unlikely case so I think we're ok for the time being.
                          case Left(err) =>
                            val promoted = skunk.Query(cmd.sql, cmd.origin, cmd.encoder, skunk.Void.codec)
                            UnknownOidException(promoted, err, ty.strategy).raiseError[F, Unit]
                          case Right(td) => UnexpectedRowsException(cmd, td).raiseError[F, Unit]
                        }
                    }
              _  <- cache.commandCache.put(cmd, ()) // on success
            } yield ()
          }
        }

      override def apply[A](query: skunk.Query[_, A], id: StatementId, ty: Typer): F[TypedRowDescription] =
        OptionT(cache.queryCache.get(query)).getOrElseF {
          exchange("describe") {
            for {
              _  <- Trace[F].put("statement-id" -> id.value)
              _  <- send(DescribeMessage.statement(id.value))
              _  <- send(Flush)
              _  <- expect { case ParameterDescription(_) => } // always ok
              rd <- flatExpect {
                      case rd @ RowDescription(_) => rd.pure[F]
                      case NoData                 => NoDataException(query).raiseError[F, RowDescription]
                    }
              td <- rd.typed(ty) match {
                      case Left(err) => UnknownOidException(query, err, ty.strategy).raiseError[F, TypedRowDescription]
                      case Right(td) =>
                        Trace[F].put("column-types" -> td.fields.map(_.tpe).mkString("[", ", ", "]")).as(td)
                    }
              _  <- ColumnAlignmentException(query, td).raiseError[F, Unit].unlessA(query.decoder.types === td.types)
              _  <- cache.queryCache.put(query, td) // on success
            } yield td
          }
        }

    }

  /** A cache for the `Describe` protocol. */
  final case class Cache[F[_]](
    commandCache: StatementCache[F, Unit],
    queryCache:   StatementCache[F, TypedRowDescription],
  ) {
    def mapK[G[_]](fk: F ~> G): Cache[G] =
      Cache(commandCache.mapK(fk), queryCache.mapK(fk))
  }

  object Cache {
    def empty[F[_]: Functor: Semigroupal: Ref.Make](
      commandCapacity: Int,
      queryCapacity:   Int,
    ): F[Cache[F]] = (
      StatementCache.empty[F, Unit](commandCapacity),
      StatementCache.empty[F, TypedRowDescription](queryCapacity)
    ).mapN(Describe.Cache(_, _))
  }

}