// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.implicits._
import skunk.Query
import skunk.util.{ CallSite, Origin, Pretty }

// Ok we want
// the statement and its origin, if known
// the arguments and their binding origin
// the logical call in prograss and its callsite, if known

class SkunkException protected[skunk](
  sql:       String,
  message:   String,
  position:  Option[Int]                  = None,
  detail:    Option[String]               = None,
  hint:      Option[String]               = None,
  history:   List[Either[Any, Any]]       = Nil,
  arguments: List[(Type, Option[String])] = Nil,
  sqlOrigin: Option[Origin]               = None,
  argumentsOrigin: Option[Origin]         = None,
  callSite: Option[CallSite]              = None
) extends Exception(message) {

  protected def framed(s: String) =
    "\u001B[4m" + s + "\u001B[24m"


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

  final protected def header: String =
    s"""|$title
        |${labeled("  Problem: ", message)}${labeled("  Detail: ", detail.orEmpty)}${labeled("     Hint: ", hint.orEmpty)}
        |
        |""".stripMargin

  final protected def statement: String = {
    val stmt = Pretty.formatMessageAtPosition(sql, message, position.getOrElse(0))
    s"""|The statement under consideration ${sqlOrigin.fold("is")(or => s"was defined\n  at $or")}
        |
        |$stmt
        |
        |""".stripMargin
  }

  final protected def exchanges: String =
    if (history.isEmpty) "" else
    s"""|Recent message exchanges:
        |
        |  ${history.map(_.fold(a => s"→ ${Console.BOLD}$a${Console.RESET}", "← " + _)).mkString("", "\n|  ", "")}
        |
        |""".stripMargin

  final protected def args: String = {

    def formatValue(s: String) =
      s"${Console.GREEN}$s${Console.RESET}"

    if (arguments.isEmpty) "" else
    s"""|and the arguments ${argumentsOrigin.fold("were")(or => s"were provided\n  at $or")}
        |
        |  ${arguments.zipWithIndex.map { case ((t, s), n) => f"$$${n+1} $t%-10s ${s.fold("NULL")(formatValue)}" } .mkString("\n|  ") }
        |
        |""".stripMargin
  }

  protected def sections: List[String] =
    List(header, statement, args, exchanges)

  final override def toString =
    sections
      .combineAll
      .lines
      .map("🔥  " + _)
      .mkString("\n", "\n", s"\n\n${getClass.getName}: $message")

}


object SkunkException {

  def fromQueryAndArguments[A](
    message: String,
    query: Query[A, _],
    args: A,
    callSite0: Option[CallSite],
    hint0: Option[String] = None,
    argsOrigin: Option[Origin] = None
  ) =
    new SkunkException(
      query.sql,
      message,
      sqlOrigin = query.sqlOrigin,
      callSite = callSite0,
      hint = hint0,
      arguments = query.encoder.types zip query.encoder.encode(args),
      argumentsOrigin = argsOrigin
    )

}