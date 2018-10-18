package skunk.net.message

import scodec.Decoder

case object ParseComplete extends BackendMessage {
  final val Tag = '1'
  def decoder = Decoder.point(ParseComplete)
}