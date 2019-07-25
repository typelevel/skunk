// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.implicits._
import skunk.Query
import skunk.data.TypedRowDescription
import skunk.net.message.RowDescription
import skunk.util.Text
import skunk.syntax.list._
import cats.data.Ior
import skunk.data.Type

case class UnknownOidException(
  query: Query[_, _],
  types: List[(RowDescription.Field, Option[TypedRowDescription.Field])],
) extends SkunkException(
  sql       = Some(query.sql),
  message   = "Unknown oid(s) in row description.",
  detail    = Some("Skunk could not interpret the row description for this query because it contains references to unknown types."),
  hint      = Some("A referenced type was created after this session was initiated, or it is in a namespace that's not on the search path."),
  sqlOrigin = Some(query.origin),
) {

  import Text.{ green, red, cyan, empty }
  implicit def stringToText(s: String): Text = Text(s)

  def unk(f: RowDescription.Field): Text =
    s"${f.typeOid}" ///${f.typeMod}"

  private def describe(ior: Ior[(RowDescription.Field, Option[TypedRowDescription.Field]), Type]): List[Text] =
    ior match {
      case Ior.Left((f1,None))        => List(green(f1.name), unk(f1),     "->", red(""), cyan(s"── unmapped column, unknown type oid"))
      case Ior.Left((f1,Some(f2)))    => List(green(f1.name), f2.tpe.name, "->", red(""), cyan( "── unmapped column"))
      case Ior.Right(t)               => List(empty,          empty,       "->", t.name,  cyan( "── missing column"))
      case Ior.Both((f1,None), t)     => List(green(f1.name), unk(f1),     "->", t.name,  cyan(s"── unknown type oid"))
      case Ior.Both((f1,Some(f2)), t) => List(green(f1.name), f2.tpe.name, "->", t.name,  if (f2.tpe === t) empty else
                                                                                          cyan( "── type mismatch"))
    }

  private def columns: String =
    s"""|The actual and asserted output columns are
        |
        |  ${Text.grid(types.align(query.decoder.types).map(describe)).intercalate(Text("\n|  ")).render}
        |
        |""".stripMargin

  override def sections: List[String] =
    super.sections :+ columns

}

