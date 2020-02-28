// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.Monoid
import cats.data.Nested
import cats.implicits._

// display combinators for styled text
final class Text private (private val content: List[Text.Segment]) {
  def ++(other: Text): Text = new Text(content ++ other.content)
  def length: Int = content.foldMap(_.body.length)
  def render: String = content.foldMap(_.render)
  def padTo(n: Int): Text =
    content match {
      case Nil => Text.padding(n)
      case _   =>
        val extra = n - length
        if (extra <= 0) this else this ++ Text.padding(extra)
    }
  override def toString: String = s"""Text("$render")"""
}

object Text {

  // Constructors

  def apply(body: String): Text = plain(body)

  lazy val plain:   String => Text = styled("", _)
  lazy val red:     String => Text = styled(Console.RED, _)
  lazy val blue:    String => Text = styled(Console.BLUE, _)
  lazy val green:   String => Text = styled(Console.GREEN, _)
  lazy val yellow:  String => Text = styled(Console.YELLOW, _)
  lazy val cyan:    String => Text = styled(Console.CYAN, _)
  lazy val magenta: String => Text = styled(Console.MAGENTA, _)

  val empty: Text = plain("")

  def padding(n: Int): Text = Text(" " * n)

  def styled(style: String, body: String): Text = new Text(List(Segment(style, body)))

  case class Segment(style: String, body: String) {
    def render: String = style + body + Console.RESET
  }
  object Segment {
    val Empty = Segment("", "")
  }

  implicit val MonoidText: Monoid[Text] =
    new Monoid[Text] {
      val empty = new Text(Nil)
      def combine(a: Text, b: Text): Text = a ++ b
    }

  def grid(rows: List[List[Text]]): List[Text] = {
    val lengths  = Nested(rows).map(_.length).value
    val paddings = LazyList.from(0).map(i => lengths.map(_.lift(i).orEmpty).max)
    rows.map(_.zipWithIndex.map { case (s, n) => s.padTo(paddings(n)) }.intercalate(Text("  ")))
  }

}