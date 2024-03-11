// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.Eq

sealed abstract case class LTree (labels: List[String]) {

  def isAncestorOf(other: LTree): Boolean =
    other.labels.startsWith(labels)

  def isDescendantOf(other: LTree): Boolean = other.isAncestorOf(this)

  override def toString: String = labels.mkString(LTree.Separator.toString())
}

object LTree {
  val Empty = new LTree(Nil) {}

  def fromLabels(s: String*): Either[String, LTree] =
    fromString(s.toList.mkString(Separator.toString()))

  def fromString(s: String): Either[String, LTree] = {

    if (s.isEmpty()) {
      Right(new LTree(Nil){})
    } else {
      // We have a failure sentinal and a helper to set it.
      var failure: String = null
      def fail(msg: String): Unit =
        failure = s"ltree parse error: $msg"

      val labels = s.split(Separator).toList

      if(labels.length > MaxTreeLength)
        fail(s"ltree size (${labels.size}) must be <= $MaxTreeLength")

      labels.foreach(l => l match {
        case ValidLabelRegex() => ()
        case _ => fail(s"invalid ltree label '$l'. Only alphanumeric characters and '_' are allowed.")
      })

      if(failure != null)
        Left(failure)
      else
        Right(new LTree(labels){})
    }
  }

  final val MaxLabelLength = 255
  final val MaxTreeLength = 65535

  private final val Separator = '.'
  private final val ValidLabelRegex = s"""^[\\p{L}0-9_]{1,$MaxLabelLength}$$""".r

  implicit val ltreeEq: Eq[LTree] = Eq.fromUniversalEquals[LTree]
}
