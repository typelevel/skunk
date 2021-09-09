// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.Eq

final case class LTree private (labels: List[String]) {

  def isAncestorOf(other: LTree): Boolean = {
    labels.isEmpty ||                 // Empty LTree is parent to all
    other.labels.startsWith(labels)   // Other labels starts with this labels
  }

  def isDescendantOf(other: LTree): Boolean = other.isAncestorOf(this)
  
  override def toString: String = labels.mkString(LTree.Separator.toString())
}

object LTree {
  val Empty = LTree(Nil)

  def unsafe(labels: String*): LTree = LTree(labels.toList)

  def fromString(s: String): Either[String, LTree] = {

    if(s.isEmpty()) {
      Right(LTree.Empty)
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
        Right(LTree(labels))
    }
  }

  val MaxLabelLength = 255
  val MaxTreeLength = 65535
  
  private val Separator = '.'
  private val ValidLabelRegex = s"""^[A-Za-z0-9_]{1,$MaxLabelLength}$$""".r
  
  implicit val ltreeEq: Eq[LTree] = Eq.fromUniversalEquals[LTree]
}