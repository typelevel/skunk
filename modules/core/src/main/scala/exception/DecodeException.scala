// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.data.Nested
import cats.syntax.all._
import skunk.{ Encoder, Decoder }
import skunk.util.Origin
// import skunk.net.Protocol
import skunk.util.Text
import skunk.util.Text.{ plain, empty, cyan, green }
import skunk.data.TypedRowDescription
import skunk.data.TypedRowDescription.Field

// todo: this with a ctor we can call for quick query, which has no portal
class DecodeException[F[_], A, B](
  data:      List[Option[String]],
  error:     Decoder.Error,
  sql:       String,
  sqlOrigin: Option[Origin],
  arguments: A,
  argumentsOrigin: Option[Origin],
  encoder:   Encoder[A],
  rowDescription: TypedRowDescription,
  cause:     Option[Throwable] = None
) extends SkunkException(
  sql             = Some(sql),
  message         = "Decoding error.",
  detail          = Some("This query's decoder was unable to decode a row of data."),
  arguments       = encoder.types.zip(encoder.encode(arguments)),
  argumentsOrigin = argumentsOrigin,
  sqlOrigin       = sqlOrigin
) {

  // def this(
  //   portal: Protocol.QueryPortal[F, A, B],
  //   data:   List[Option[String]],
  //   error:  Decoder.Error,
  //   rowDescription: TypedRowDescription
  // ) = this(
  //   data,
  //   error,
  //   portal.preparedQuery.query.sql,
  //   Some(portal.preparedQuery.query.origin),
  //   portal.arguments,
  //   Some(portal.argumentsOrigin),
  //   portal.preparedQuery.query.encoder,
  //   rowDescription
  // )

  val MaxValue: Int = 15

  // Truncate column values at MaxValue char
  private val dataʹ = Nested(data).map { s =>
    if (s.length > MaxValue) s.take(MaxValue) + "⋯" else s
  } .value

  def describe(col: ((Field, Int), Option[String])): List[Text] = {
    val ((t, n), op) = col
    List(
      green(t.name),
      Text(t.tpe.name),
      plain("->"),
      green(op.getOrElse("NULL")),
      if (n === error.offset) cyan(s"├── ${error.message}${cause.foldMap(_ => " (see below)")}") else empty
      // TODO - make a fence for errors that span, like
      //        ├───── foo bar
      //        │
    )
  }

  final protected def row: String =
    s"""|The row in question returned the following values (truncated to $MaxValue chars).
        |
        |  ${Text.grid(rowDescription.fields.zipWithIndex.zip(dataʹ).map(describe)).intercalate(plain("\n|  ")).render}
        |
        |""".stripMargin

  final protected def exception: String =
    cause.foldMap { e =>
      s"""|The decoder threw the following exception:
          |
          |  ${e.getClass.getName}: ${e.getMessage}
          |    ${e.getStackTrace.mkString("\n    ")}
          |
          |""".stripMargin
    }

  override def sections: List[String] =
    super.sections :+ row :+ exception

}
