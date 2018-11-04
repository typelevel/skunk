// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs._

case class ParameterStatus(name: String, value: String) extends BackendMessage

object ParameterStatus {
  final val Tag = 'S'
  def decoder = (cstring ~ cstring).map(apply)
}