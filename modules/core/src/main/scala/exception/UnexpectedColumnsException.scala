// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.implicits._
import skunk.Command
import skunk.data.`Type`
import skunk.net.message.RowDescription
import skunk.util.Text

case class UnexpectedRowsException(
  command: Command[_],
  rd:    RowDescription
) extends SkunkException(
  sql       = Some(command.sql),
  message   = "Statement returns data.",
  hint      = Some(s"This ${framed("command")} returns rows and should be a ${framed("query")}."),
  sqlOrigin = Some(command.origin),
) {

  import Text.green
  implicit def stringToText(s: String): Text = Text(s)

  private def describeType(f: RowDescription.Field): Text =
    Text(Type.forOid(f.typeOid).fold(s"Unknown(${f.typeOid})")(_.name))

  private def describe(f: RowDescription.Field): List[Text] =
    List(green(f.name), describeType(f))

  private def columns: String =
    s"""|The unexpected output columns are
        |
        |  ${Text.grid(rd.fields.map(describe)).intercalate(Text("\n|  ")).render}
        |
        |""".stripMargin

  override def sections =
    super.sections :+ columns

}

