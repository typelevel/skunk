// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.bits.ByteVector
import scodec.codecs.bytes

final case class CopyOutResponse(data: ByteVector) extends BackendMessage {
  override def toString = s"CopyOutResponse(...)"
}

object CopyOutResponse {
  final val Tag = 'H'
  val decoder: Decoder[CopyOutResponse] = bytes.map(bv => CopyOutResponse(bv)) // TODO!
}
