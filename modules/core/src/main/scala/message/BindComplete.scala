package skunk

package message

import scodec.Decoder

case object BindComplete extends BackendMessage {

  val Tag = '2'

  def decoder: Decoder[BindComplete.type] =
    Decoder.point(BindComplete)

}