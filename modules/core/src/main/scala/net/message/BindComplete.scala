package skunk.net.message

import scodec.Decoder

 object BindComplete extends BackendMessage {

  final val Tag = '2'

  def decoder: Decoder[BindComplete.type] =
    Decoder.point(BindComplete)

}