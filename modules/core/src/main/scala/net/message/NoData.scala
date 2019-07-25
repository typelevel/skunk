// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

case object NoData extends BackendMessage {
  final val Tag = 'n'
  def decoder: Decoder[NoData.type] = Decoder.point(NoData)

}