// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.implicits._
import skunk.util.Pretty

class SkunkException protected[skunk](
  sql:       String,
  message:   String,
  position:  Option[Int]                  = None,
  detail:    Option[String]               = None,
  hint:      Option[String]               = None,
  history:   List[Either[Any, Any]]       = Nil,
  arguments: List[(Type, Option[String])] = Nil
) extends Exception(message) {

  protected def title: String =
    getClass.getSimpleName

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
    s"""|The statement under consideration is
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
    s"""|and the arguments were
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
