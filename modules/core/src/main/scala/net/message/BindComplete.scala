// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

case object BindComplete extends BackendMessage {
  final val Tag = '2'
  def decoder = Decoder.point(BindComplete)
}