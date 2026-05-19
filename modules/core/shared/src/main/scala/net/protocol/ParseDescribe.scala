// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats.syntax.all._
import skunk.net.MessageSocket
import skunk.net.Protocol.StatementId
import skunk.net.message.{ Describe => DescribeMessage, Parse => ParseMessage, _ }
import skunk.util.Typer
import skunk.data.TypedRowDescription
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer
import cats.data.OptionT
import cats.effect.MonadCancel
import skunk.Statement
import skunk.exception.*
import skunk.util.Namer
import skunk.net.protocol.exchange
import org.typelevel.otel4s.metrics.Histogram

trait ParseDescribe[F[_]] {
  def command[A](cmd: skunk.Command[A], ty: Typer): F[StatementId]
  def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[(StatementId, TypedRowDescription)]
}

object ParseDescribe {

  def apply[F[_]: Exchange: MessageSocket: Tracer: Namer](cache: Describe.Cache[F], parseCache: Parse.Cache[F], opDuration: Histogram[F, Double])(
    implicit ev: MonadCancel[F, Throwable]
  ): ParseDescribe[F] =
    new ParseDescribe[F] {
      def syncAndFail(statement: Statement[_], info: Map[Char, String]): F[Unit] =
        for {
          hi <- history(Int.MaxValue)
          _  <- send(Sync)
          _  <- expect { case ReadyForQuery(_) => }
          a  <- new PostgresErrorException(
                  sql       = statement.sql,
                  sqlOrigin = Some(statement.origin),
                  info      = info,
                  history   = hi,
                ).raiseError[F, Unit]
        } yield a

      def parseExchange(stmt: Statement[_], ty: Typer)(span: Span[F]): F[(F[StatementId], StatementId => F[Unit])] = 
        stmt.encoder.oids(ty) match {

          case Right(os) if os.length > Short.MaxValue =>
            TooManyParametersException(stmt).raiseError[F, (F[StatementId], StatementId => F[Unit])]

          case Right(os) =>

            def addStatement(id: StatementId): F[Unit] =
              span.addAttributes(
                Attribute("statement-name", id.value),
                Attribute("statement-sql",  stmt.sql),
                Attribute("statement-parameter-types", os.map(n => ty.typeForOid(n, -1).fold(n.toString)(_.toString)).mkString("[", ", ", "]"))
              )

            OptionT(parseCache.value.get(stmt)).map(id => (addStatement(id).as(id), (_:StatementId) => ().pure)).getOrElse {
              val pre = for {
                id <- nextName("statement").map(StatementId(_))
                _  <- addStatement(id)
                _  <- send(ParseMessage(id.value, stmt.sql, os))
              } yield id
              val post = (id: StatementId) => for {
                _  <- flatExpect {
                        case ParseComplete       => ().pure[F]
                        case ErrorResponse(info) => syncAndFail(stmt, info)
                      }
                _  <- parseCache.value.put(stmt, id)
              } yield ()

              (pre, post)
            }
          case Left(err) =>
            UnknownTypeException(stmt, err, ty.strategy).raiseError[F, (F[StatementId], StatementId => F[Unit])]

        }

      override def command[A](cmd: skunk.Command[A], ty: Typer): F[StatementId] = {

        def describeExchange(span: Span[F]): F[(StatementId => F[Unit], F[Unit])] = {
          def addStatementId(id: StatementId): F[Unit] =
            span.addAttribute(Attribute("statement-id", id.value))

          OptionT(cache.commandCache.get(cmd)).as(((id: StatementId) => addStatementId(id), ().pure[F])).getOrElse {
            val pre = (id: StatementId) => for {
              _  <- addStatementId(id)
              _  <- send(DescribeMessage.statement(id.value))
            } yield ()

            val post: F[Unit] = for {
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

            (pre, post)
          }
        }

        exchange("parse+describe", opDuration) { (span: Span[F]) =>
          parseExchange(cmd, ty)(span).flatMap { case (preParse, postParse) =>
            describeExchange(span).flatMap { case (preDesc, postDesc) =>
              for {
                id <- preParse
                _  <- preDesc(id)
                _  <- send(Flush)
                _  <- postParse(id)
                _  <- postDesc
              } yield id
            }
          }
        }  

      }

      override def apply[A, B](query: skunk.Query[A, B], ty: Typer): F[(StatementId, TypedRowDescription)] = {

        def describeExchange(span: Span[F]): F[(StatementId => F[Unit], F[TypedRowDescription])] = {
          def addStatementId(id: StatementId): F[Unit] =
            span.addAttribute(Attribute("statement-id", id.value))

          def addColumnTypes(td: TypedRowDescription): F[Unit] =
            span.addAttribute(Attribute("column-types", td.fields.map(_.tpe).mkString("[", ", ", "]")))

          OptionT(cache.queryCache.get(query)).map { rd =>
            val pre  = (id: StatementId) => addStatementId(id)
            val post = addColumnTypes(rd).as(rd)
            (pre, post)
          }.getOrElse {
            val pre = (id: StatementId) =>
              for {
                _  <- addStatementId(id)
                _  <- send(DescribeMessage.statement(id.value))
              } yield ()

            val post =
              for {
                _  <- expect { case ParameterDescription(_) => } // always ok
                rd <- flatExpect {
                        case rd @ RowDescription(_) => rd.pure[F]
                        case NoData                 => NoDataException(query).raiseError[F, RowDescription]
                      }
                td <- rd.typed(ty) match {
                        case Left(err) => UnknownOidException(query, err, ty.strategy).raiseError[F, TypedRowDescription]
                        case Right(td) => addColumnTypes(td).as(td)
                      }
                _  <- ColumnAlignmentException(query, td).raiseError[F, Unit].unlessA(query.isDynamic || query.decoder.types === td.types)
                _  <- cache.queryCache.put(query, td) // on success
              } yield td

            (pre, post)
          }
        }

        
        exchange("parse+describe", opDuration) { (span: Span[F]) => 
          parseExchange(query, ty)(span).flatMap { case (preParse, postParse) =>
            describeExchange(span).flatMap { case (preDesc, postDesc) =>
              for {
                id <- preParse
                _  <- preDesc(id)
                _  <- send(Flush)
                _  <- postParse(id)
                rd <- postDesc
              } yield (id, rd)
            }
          }
        }  
      }

    }

}
