package skunk.net.message

import scodec.Decoder

case object CloseComplete extends BackendMessage {

  final val Tag = '3'

  def decoder: Decoder[CloseComplete.type] =
    Decoder.point(CloseComplete)

}