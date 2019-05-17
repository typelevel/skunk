// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.MonadError
import cats.implicits._
import skunk.exception.{ UnexpectedRowsException, ColumnAlignmentException, NoDataException }
import skunk.net.MessageSocket
import skunk.net.Protocol.StatementId
import skunk.net.message.{ Describe => DescribeMessage, _ }
import skunk.util.Typer

trait Describe[F[_]] {
  def apply(cmd: skunk.Command[_], id: StatementId, ty: Typer): F[Unit]
  def apply[A](query: skunk.Query[_, A], id: StatementId, ty: Typer): F[RowDescription]
}

object Describe {

  def apply[F[_]: MonadError[?[_], Throwable]: Exchange: MessageSocket]: Describe[F] =
    new Describe[F] {

      def apply(cmd: skunk.Command[_], id: StatementId, ty: Typer): F[Unit] =
        exchange {
          for {
            _  <- send(DescribeMessage.statement(id.value))
            _  <- send(Flush)
            _  <- expect { case ParameterDescription(_) => } // always ok
            _  <- flatExpect {
                    case NoData                 => ().pure[F]
                    case rd @ RowDescription(_) => UnexpectedRowsException(cmd, rd, ty).raiseError[F, Unit]
                  }
          } yield ()
        }

      def apply[A](query: skunk.Query[_, A], id: StatementId, ty: Typer): F[RowDescription] =
        exchange {
          for {
            _  <- send(DescribeMessage.statement(id.value))
            _  <- send(Flush)
            _  <- expect { case ParameterDescription(_) => } // always ok
            rd <- flatExpect {
                    case rd @ RowDescription(_) => rd.pure[F]
                    case NoData                 => NoDataException(query).raiseError[F, RowDescription]
                  }

            // ok here is where we may encounter unknown types … if someone specifies foo = enum("foo")
            // and declares we have a column of type foo, what we will get back is just an oid and
            // we will need to look it up in the database to see whether it's the same as the enum
            // type foo. so we need a way to enrich the row description and promote the oids to
            // types in a way that can consult the system tables.

            // so i think types should not have oids, but we do have a lookup like Type.forOid
            // that also takes a Ref[Map[oid, Type]] that it might update, returning F[Type].

            // the problem is that in the simple query protocol we don't have time to ask the
            // database any questions about the row description because we're already getting row
            // data back. we could defer decoding i guess, then check after ... ?
            types = rd.fields.map(f => ty.typeForOid(f.typeOid, f.typeMod).get)
            _  <- ColumnAlignmentException(query, rd, ty).raiseError[F, Unit].unlessA(query.decoder.types === types)
          } yield rd
        }

    }

}