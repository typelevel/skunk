// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.syntax.foldable._
import skunk.util.Text

class StartupException private[skunk] (
  info: Map[Char, String],
  properties: Map[String, String]
) extends PostgresErrorException(
  sql = "", // grim, fix this
  sqlOrigin = None,
  info = info,
  history = Nil
) {

  import Text.green
  implicit def stringToText(s: String): Text = Text(s)

  private def describe(k: String, v: String): List[Text] =
    List(green(k), "=", green(v))

  override def header: String = "Startup negotiation failed.\n\n" + super.header

  // grim, fix this too
  override def statement: String =
    s"""|Startup properties were:
        |
        |  ${Text.grid(properties.toList.map { case (k, v) => describe(k, v) }).intercalate(Text("\n|  ")).render}
        |
        |""".stripMargin

}
