// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs._

case class ParameterStatus(name: String, value: String) extends BackendMessage

object ParameterStatus {
  final val Tag = 'S'
  val decoder: Decoder[ParameterStatus] = (cstring ~ cstring).map(apply)
}