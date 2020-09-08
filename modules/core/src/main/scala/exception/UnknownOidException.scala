// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.syntax.all._
import skunk._
import skunk.data.TypedRowDescription
import skunk.net.message.RowDescription
import skunk.util.Text
import skunk.syntax.list._
import cats.data.Ior
import skunk.data.Type
import Strategy._

case class UnknownOidException(
  query: Query[_, _],
  types: List[(RowDescription.Field, Option[TypedRowDescription.Field])],
  strategy: Strategy
) extends SkunkException(
  sql       = Some(query.sql),
  message   = "Unknown oid(s) in row description.",
  detail    = Some("Skunk could not decode the row description for this query because it cannot determine the Scala types corresponding to one or more Postgres type oids."),
  hint      = Some {
    strategy match {
      case SearchPath   => "A referenced type was created after this session was initiated, or it is in a namespace that's not on the search path (see note below)."
      case BuiltinsOnly => "Try changing your typing strategy (see note below)."
     }
  },
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

  private def codeHint: String =
    strategy match {
      case SearchPath   =>
        s"""|Your typing strategy is ${Console.GREEN}Strategy.SearchPath${Console.RESET} which can normally detect user-defined types; however Skunk
            |only reads the system catalog once (when the Session is initialized) so user-created types are not
            |immediately usable. If you are creating a new type you might consider doing it on a single-use session,
            |before initializing other sessions that use it.
            |""".stripMargin
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
    s"""|The actual and asserted output columns are
        |
        |  ${Text.grid(types.align(query.decoder.types).map(describe)).intercalate(Text("\n|  ")).render}
        |
        |""".stripMargin

  override def sections: List[String] =
    super.sections :+ columns :+ codeHint

}

