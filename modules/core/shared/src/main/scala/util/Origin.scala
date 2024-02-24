// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import org.tpolecat.sourcepos.SourcePos

final case class Origin(file: String, line: Int) {

  def toCallSite(methodName: String): CallSite =
    CallSite(methodName, this)

  override def toString =
    s"$file:$line"

}

object Origin {

  val unknown = Origin("«skunk internal»", 0)

  implicit def instance(implicit sp: SourcePos): Origin =
    Origin(sp.file, sp.line)

}

final case class CallSite(methodName: String, origin: Origin)

/**
 * A value, paired with an optional `Origin`. We use this to trace user-supplied values back to
 * where they were defined or introduced.
 */
final case class Located[A](a: A, origin: Option[A] = None)
