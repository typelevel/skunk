// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.syntax.all._
import org.typelevel.otel4s.Attribute
import skunk.data.{Encoded, Type}
import skunk.Query
import skunk.util.{ CallSite, Origin, Pretty }

class SkunkException protected[skunk](
  val sql:             Option[String],
  val message:         String,
  val position:        Option[Int]                   = None,
  val detail:          Option[String]                = None,
  val hint:            Option[String]                = None,
  val history:         List[Either[Any, Any]]        = Nil,
  val arguments:       List[(Type, Option[Encoded])] = Nil,
  val sqlOrigin:       Option[Origin]                = None,
  val argumentsOrigin: Option[Origin]                = None,
  val callSite:        Option[CallSite]              = None
) extends Exception(message)  {

  def fields: List[Attribute[_]] = {

    val builder = List.newBuilder[Attribute[_]]

    builder += Attribute("error.message", message)

    sql     .foreach(a => builder += Attribute("error.sql"      , a))
    position.foreach(a => builder += Attribute("error.position"   , a.toLong))
    detail  .foreach(a => builder += Attribute("error.detail"   , a))
    hint    .foreach(a => builder += Attribute("error.hint"     , a))

    (arguments.zipWithIndex).foreach { case ((typ, os), n) =>
      builder += Attribute(s"error.argument.${n + 1}.type"  , typ.name)
      builder += Attribute(s"error.argument.${n + 1}.value" , os.map(_.toString).getOrElse[String]("NULL"))
    }

    sqlOrigin.foreach { o =>
      builder += Attribute("error.sqlOrigin.file" , o.file)
      builder += Attribute("error.sqlOrigin.line" , o.line.toLong)
    }

    argumentsOrigin.foreach { o =>
      builder += Attribute("error.argumentsOrigin.file" , o.file)
      builder += Attribute("error.argumentsOrigin.line" , o.line.toLong)
    }

    callSite.foreach { cs =>
      builder += Attribute("error.callSite.origin.file"   , cs.origin.file)
      builder += Attribute("error.callSite.origin.line"   , cs.origin.line.toLong)
      builder += Attribute("error.callSite.origin.method" , cs.methodName)
    }

    builder.result()
  }

  protected def title: String =
    callSite.fold(getClass.getSimpleName) { case CallSite(name, origin) =>
      s"Skunk encountered a problem related to use of ${framed(name)}\n  at $origin"
    }

  protected def width = 80 // wrap here

  def labeled(label: String, s: String): String =
    if (s.isEmpty) "" else {
      "\n|" +
      label + Console.CYAN + Pretty.wrap(
        width - label.length,
        s,
        s"${Console.RESET}\n${Console.CYAN}" + label.map(_ => ' ')
      ) + Console.RESET
    }

  protected def header: String =
    s"""|
        |$title
        |${labeled("  Problem: ", message)}${labeled("   Detail: ", detail.orEmpty)}${labeled("     Hint: ", hint.orEmpty)}
        |
        |""".stripMargin

  protected def statement: String =
    sql.foldMap { sql =>
      val stmt = Pretty.formatMessageAtPosition(sql, message, position.getOrElse(0))
      s"""|The statement under consideration ${sqlOrigin.fold("is")(or => s"was defined\n  at $or")}
          |
          |$stmt
          |
          |""".stripMargin
    }

  // TODO: Not clear if this is useful, disabled for now
  // protected def exchanges: String =
  //   if (history.isEmpty) "" else
  //   s"""|Recent message exchanges:
  //       |
  //       |  ${history.map(_.fold(a => s"→ ${Console.BOLD}$a${Console.RESET}", "← " + _)).mkString("", "\n|  ", "")}
  //       |
  //       |""".stripMargin

  protected def args: String = {

    def formatValue(s: Encoded) =
      s"${Console.GREEN}$s${Console.RESET}"

    if (arguments.isEmpty) "" else
    s"""|and the arguments ${argumentsOrigin.fold("were")(or => s"were provided\n  at $or")}
        |
        |  ${arguments.zipWithIndex.map { case ((t, s), n) => f"$$${n+1} $t%-10s ${s.fold("NULL")(formatValue)}" } .mkString("\n|  ") }
        |
        |""".stripMargin
  }

  protected def sections: List[String] =
    List(header, statement, args) //, exchanges)

  final override def getMessage =
    sections
      .combineAll
      .linesIterator
      .map("🔥  " + _)
      .mkString("\n", "\n", s"\n\n${getClass.getName}: $message")

}


object SkunkException {

  def fromQueryAndArguments[A](
    message:    String,
    query:      Query[A, _],
    args:       A,
    callSite:   Option[CallSite],
    hint:       Option[String] = None,
    argsOrigin: Option[Origin] = None
  ) =
    new SkunkException(
      Some(query.sql),
      message,
      sqlOrigin       = Some(query.origin),
      callSite        = callSite,
      hint            = hint,
      arguments       = query.encoder.types zip query.encoder.encode(args),
      argumentsOrigin = argsOrigin
    )

}
