package skunk.net.message

import scodec.Decoder

case object ParseComplete extends BackendMessage {

  val Tag = '1'

  def decoder: Decoder[ParseComplete.type] =
    Decoder.point(ParseComplete)

}