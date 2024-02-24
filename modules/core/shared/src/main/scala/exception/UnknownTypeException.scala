// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.syntax.foldable._
import skunk.net.message.RowDescription
import skunk.util.Text
import skunk.data.Type
import skunk.util.Typer
import skunk.util.Typer.Strategy.BuiltinsOnly
import skunk.util.Typer.Strategy.SearchPath

case class UnknownTypeException(
  query:    skunk.Statement[_],
  types:    List[(Type, Option[Int])],
  strategy: Typer.Strategy
) extends SkunkException(
  sql       = Some(query.sql),
  message   = "Type(s) not found.",
  detail    = Some("Skunk could not encode the parameter list for this statement because it cannot determine the Postgres oid(s) for one or more types."),
  hint      = Some {
    strategy match {
      case SearchPath   => "A referenced type was created after this session was initiated, or it is in a namespace that's not on the search path."
      case BuiltinsOnly => "Try changing your typing strategy (see note below)."
     }
  },
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

  private def codeHint: String =
    strategy match {
      case SearchPath   => ""
      case BuiltinsOnly =>
        s"""|Your typing strategy is ${Console.GREEN}Strategy.BuiltinsOnly${Console.RESET} which does not know about user-defined
            |types such as enums. To include user-defined types add the following argument when you
            |construct your session resource.
            |
            |  ${Console.GREEN}strategy = Strategy.SearchPath${Console.RESET}
            |
            |""".stripMargin
    }

  private def columns: String =
    s"""|Parameter indices and types are
        |
        |  ${Text.grid(types.zipWithIndex.map { case ((t, o), i) => describe(i, t, o) }).intercalate(Text("\n|  ")).render}
        |
        |""".stripMargin

  override def sections: List[String] =
    super.sections :+ columns :+ codeHint

}

