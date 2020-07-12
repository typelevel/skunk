// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

case object CopyDone extends BackendMessage {
  final val Tag = 'c'
  val decoder: Decoder[CopyDone.type] = Decoder.point(CopyDone)
}