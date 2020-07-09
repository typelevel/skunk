// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.implicits._
import skunk.data.Type
import skunk.Query
import skunk.util.{ CallSite, Origin, Pretty }
import natchez.Fields
import natchez.TraceValue

class SkunkException protected[skunk](
  val sql:             Option[String],
  val message:         String,
  val position:        Option[Int]                  = None,
  val detail:          Option[String]               = None,
  val hint:            Option[String]               = None,
  val history:         List[Either[Any, Any]]       = Nil,
  val arguments:       List[(Type, Option[String])] = Nil,
  val sqlOrigin:       Option[Origin]               = None,
  val argumentsOrigin: Option[Origin]               = None,
  val callSite:        Option[CallSite]             = None
) extends Exception(message) with Fields with scala.util.control.NoStackTrace {

  override def fields: Map[String, TraceValue] = {

    var map: Map[String, TraceValue] = Map.empty

    map += "error.message" -> message

    sql     .foreach(a => map += "error.sql"      -> a)
    position.foreach(a => map += "error.position" -> a)
    detail  .foreach(a => map += "error.detail"   -> a)
    hint    .foreach(a => map += "error.hint"     -> a)

    (arguments.zipWithIndex).foreach { case ((typ, os), n) =>
      map += s"error.argument.${n + 1}.type"  -> typ.name
      map += s"error.argument.${n + 1}.value" -> os.getOrElse[String]("NULL")
    }

    sqlOrigin.foreach { o =>
      map += "error.sqlOrigin.file" -> o.file
      map += "error.sqlOrigin.line" -> o.line
    }

    argumentsOrigin.foreach { o =>
      map += "error.argumentsOrigin.file" -> o.file
      map += "error.argumentsOrigin.line" -> o.line
    }

    callSite.foreach { cs =>
      map += "error.callSite.origin.file"   -> cs.origin.file
      map += "error.callSite.origin.line"   -> cs.origin.line
      map += "error.callSite.origin.method" -> cs.methodName
    }

    map
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
    List(header, statement, args) //, exchanges)

  final override def toString =
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