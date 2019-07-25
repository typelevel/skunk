// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

case object ParseComplete extends BackendMessage {
  final val Tag = '1'
  def decoder: Decoder[ParseComplete.type] = Decoder.point(ParseComplete)
}