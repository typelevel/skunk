// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class Origin(file: String, line: Int) {
  override def toString =
    s"$file:$line"
}

object Origin {

  implicit def instance: Origin =
    macro OriginMacros.instance_impl

  class OriginMacros(val c: blackbox.Context) {
    import c.universe._
    def instance_impl: Tree = {
      val file = c.enclosingPosition.source.path
      val line = c.enclosingPosition.line
      q"_root_.skunk.util.Origin($file, $line)"
    }
  }

}

final case class CallSite(methodName: String, origin: Origin)

/**
 * A value, paired with an optional `Origin`. We use this to trace user-supplied values back to
 * where they were defined or introduced.
 */
final case class Located[A](a: A, origin: Option[A] = None)
