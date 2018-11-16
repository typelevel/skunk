// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import sourcecode.{ File, Line }

final case class Origin(file: String, line: Int) {
  override def toString =
    s"$file:$line"
}

object Origin {

  implicit def instance(implicit f: File, l: Line): Origin =
    Origin(f.value, l.value)

}