package skunk.net.message

import scodec.Decoder

case object NoData extends BackendMessage {
  final val Tag = 'n'
  def decoder = Decoder.point(NoData)

}