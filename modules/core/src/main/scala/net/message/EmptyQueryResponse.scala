// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

case object EmptyQueryResponse extends BackendMessage {
  final val Tag = 'I'
  val decoder: Decoder[EmptyQueryResponse.type] = Decoder.point(EmptyQueryResponse)
}