// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import skunk._
import skunk.codec.all._
import skunk.syntax.all._


object Issue990 {
  def updateBy[A](where: Fragment[A]): Command[String *: A *: EmptyTuple] =
    sql"""UPDATE foo SET bar = $text WHERE $where;""".command
}
