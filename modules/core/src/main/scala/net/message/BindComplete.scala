package skunk.net.message

import scodec.Decoder

case object BindComplete extends BackendMessage {
  final val Tag = '2'
  def decoder = Decoder.point(BindComplete)
}