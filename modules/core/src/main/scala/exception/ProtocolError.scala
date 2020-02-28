// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.implicits._
import skunk.net.message.BackendMessage
import skunk.util.Origin
import skunk.util.Pretty

class ProtocolError protected[skunk](
  val message:  BackendMessage,
  val origin:   Origin
) extends Error(s"Unexpected backend message: $message") with scala.util.control.NoStackTrace {

  protected def title: String =
    s"An unhandled backend message was encountered\n  at $origin"

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
        |${labeled("  Message: ", message.toString)}
        |
        |This is an ${Console.UNDERLINED}implementation error${Console.RESET} in Skunk.
        |Please report a bug with the full contents of this error message.
        |""".stripMargin

  protected def sections: List[String] =
    List(header) //, exchanges)

  final override def toString: String =
    sections
      .combineAll
      .linesIterator
      .map("🔥  " + _)
      .mkString("\n", "\n", s"\n\n${getClass.getName}: $message")

}

