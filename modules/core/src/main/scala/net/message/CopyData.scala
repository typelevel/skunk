// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.bits.ByteVector
import scodec.codecs.bytes

final case class CopyData(data: ByteVector) extends BackendMessage {
  override def toString = s"CopyData(...)"
}

object CopyData {
  final val Tag = 'd'
  val decoder: Decoder[CopyData] = bytes.map(bv => CopyData(bv)) // TODO!
}
