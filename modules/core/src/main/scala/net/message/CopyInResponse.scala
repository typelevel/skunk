// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.bits.ByteVector
import scodec.codecs.bytes

final case class CopyInResponse(data: ByteVector) extends BackendMessage {
  override def toString = s"CopyInResponse(...)"
}

object CopyInResponse {
  final val Tag = 'G'
  val decoder: Decoder[CopyInResponse] = bytes.map(bv => CopyInResponse(bv)) // TODO!
}
