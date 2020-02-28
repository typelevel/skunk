// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.implicits._
import skunk.net.message.RowDescription
import skunk.util.Text
import skunk.data.Type

case class UnknownTypeException(
  query: skunk.Statement[_],
  types: List[(Type, Option[Int])]
) extends SkunkException(
  sql       = Some(query.sql),
  message   = "Unknown type(s) in statement.",
  detail    = Some("Skunk could not determine the Postgres oid for one or more parameter types."),
  hint      = Some("A referenced type does not exist, was created after this session was initiated, or is in a namespace that's not on the search path."),
  sqlOrigin = Some(query.origin),
) {

  import Text.{ green, cyan, empty }
  implicit def stringToText(s: String): Text = Text(s)

  def unk(f: RowDescription.Field): Text =
    s"${f.typeOid}" ///${f.typeMod}"

  private def describe(i: Int, t: Type, oo: Option[Int]): List[Text] =
    oo match {
      case Some(_) => List(green(s"$$${i+1}"), t.name, empty)
      case None    => List(green(s"$$${i+1}"), t.name, cyan("── unknown type"))
    }

  private def columns: String =
    s"""|Parameter indices and types are
        |
        |  ${Text.grid(types.zipWithIndex.map { case ((t, o), i) => describe(i, t, o) }).intercalate(Text("\n|  ")).render}
        |
        |""".stripMargin

  override def sections: List[String] =
    super.sections :+ columns

}

