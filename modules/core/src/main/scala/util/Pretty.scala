// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

object Pretty {

  // Insert a carat and message into the given string at the specified position, dealing with
  // newlines in a sensible way. We also trim leading/trailing whitespace and un-indent everything.
  def formatMessageAtPosition(source: String, message: String, pos: Int): String = {

    // Add the error message
    val s1 = source.lines.toList.foldLeft((0, "")) { case ((n, acc), s) =>
      val nʹ = n + s.length + 1
      (nʹ, s"$acc\n" + {
          if (pos > 0 && pos >= n && pos <= nʹ) {
            s"$s\n${" " * (pos - n - 1)}${Console.CYAN}└─── $message${Console.RESET}${Console.GREEN}"
          } else s
        }
      )
    } ._2.drop(1)

    // Remove leading and trailing empty lines
    val s2 =
      s1.lines.toList.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse.mkString("\n")

    // Normalize tabs
    val s3 = {
      val drop = s2.lines.map(_.takeWhile(_ == ' ').length).min
      s2.lines.map(s => s"  ${Console.GREEN}" + s.drop(drop) + Console.RESET).mkString("\n")
    }

    s3

  }

  def wrap(w: Int, s: String, delim: String = "\n"): String =
    if (w >= s.length) s else {
      s.lastIndexWhere(_ == ' ', w) match {
        case -1 => wrap(w + 1, s, delim)
        case n  => val (s1, s2) = s.splitAt(n)
          s1 + delim + wrap(w, s2.trim, delim)
      }
    }

}