// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import skunk.util.Origin

trait Statement[A] {
  def sql:     String
  def origin:  Option[Origin]
  def encoder: Encoder[A]
}