// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.data.Nested
import cats.implicits._
import skunk.{ Encoder, Decoder }
import skunk.data.Type
import skunk.util.Origin
import skunk.net.message.RowDescription
import skunk.net.Protocol
import skunk.util.Text
import skunk.util.Text.{ plain, empty, cyan, green }

// todo: this with a ctor we can call for quick query, which has no portal
class DecodeException[F[_], A, B](
  data:      List[Option[String]],
  error:     Decoder.Error,
  sql:       String,
  sqlOrigin: Option[Origin],
  arguments: A,
  argumentsOrigin: Option[Origin],
  encoder:   Encoder[A],
  rowDescription: RowDescription
) extends SkunkException(
  sql             = sql,
  message         = "Decoding error.",
  hint            = Some("This query's decoder was unable to decode a data row."),
  arguments       = encoder.types.zip(encoder.encode(arguments)),
  argumentsOrigin = argumentsOrigin,
  sqlOrigin       = sqlOrigin
) {

  def this(
    portal: Protocol.QueryPortal[F, A, B],
    data:   List[Option[String]],
    error:  Decoder.Error
  ) = this(
    data,
    error,
    portal.preparedQuery.query.sql,
    Some(portal.preparedQuery.query.origin),
    portal.arguments,
    Some(portal.argumentsOrigin),
    portal.preparedQuery.query.encoder,
    portal.preparedQuery.rowDescription
  )

  val MaxValue = 15

  // Truncate column values at MaxValue char
  private val dataʹ = Nested(data).map { s =>
    if (s.length > MaxValue) s.take(MaxValue) + "⋯" else s
  } .value

  private def describeType(f: RowDescription.Field): Text =
    plain(Type.forOid(f.typeOid).fold(s"Unknown(${f.typeOid})")(_.name))

  def describe(col: ((RowDescription.Field, Int), Option[String])): List[Text] = {
    val ((t, n), op) = col
    List(
      green(t.name),
      describeType(t),
      plain("->"),
      green(op.getOrElse("NULL")),
      if (n === error.offset) cyan(s"── ${error.error}") else empty
    )
  }

  final protected def row: String =
    s"""|The row in question returned the following values (truncated to $MaxValue chars).
        |
        |  ${Text.grid(rowDescription.fields.zipWithIndex.zip(dataʹ).map(describe)).intercalate(plain("\n|  ")).render}
        |""".stripMargin

  override def sections =
    super.sections :+ row

}
