package skunk.proto.message

import scodec.bits.ByteVector
import scodec.codecs.bytes

/** Default interpretation of a backend message if the tag is unknown to Skunk. */
final case class UnknownMessage(tag: Byte, data: ByteVector) extends BackendMessage {
  override def toString = s"UnknownMessage(${tag.toChar}, $data)"
}

object UnknownMessage {
  def decoder(tag: Byte) = bytes.map(bv => UnknownMessage(tag, bv))
}
