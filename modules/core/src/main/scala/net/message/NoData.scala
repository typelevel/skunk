package skunk.net.message

import scodec.Decoder

case object NoData extends BackendMessage {

  val Tag = 'n'

  def decoder: Decoder[NoData.type] =
    Decoder.point(NoData)

}