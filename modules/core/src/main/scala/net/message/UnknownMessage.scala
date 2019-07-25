// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.bits.ByteVector
import scodec.codecs.bytes

/** Default interpretation of a backend message if the tag is unknown to Skunk. */
final case class UnknownMessage(tag: Byte, data: ByteVector) extends BackendMessage {
  override def toString = s"UnknownMessage(${tag.toChar}, $data)"
}

object UnknownMessage {
  def decoder(tag: Byte): Decoder[UnknownMessage] = bytes.map(bv => UnknownMessage(tag, bv))
}
