package skunk
package proto
package message

import scodec.Decoder

case object CloseComplete extends BackendMessage {

  val Tag = '3'

  def decoder: Decoder[CloseComplete.type] =
    Decoder.point(CloseComplete)

}