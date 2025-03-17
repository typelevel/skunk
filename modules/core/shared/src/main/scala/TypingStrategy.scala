// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.Order

sealed trait TypingStrategy

object TypingStrategy {

  /**
   * This strategy supports built-in Postgres types only, and does not need a database round-trip
   * for initialization. This is the fastest strategy and is appropriate when you are not using
   * any user-defined types (this includes enums).
   */
  case object BuiltinsOnly extends TypingStrategy

  /**
   * This strategy supports built-in Postgres types, as well as types that are defined in
   * namespaces on the session search path. This is the default strategy.
   */
  case object SearchPath extends TypingStrategy

  implicit val OrderTypingStrategy: Order[TypingStrategy] =
    Order.by {
      case BuiltinsOnly => 0
      case SearchPath   => 1
    }
}
