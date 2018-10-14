package skunk.net.message

import scodec.Decoder
import scodec.codecs._

case class  ParameterStatus(name: String, value: String) extends BackendMessage

object ParameterStatus {

  // Byte1('S') - Identifies the message as a run-time parameter status report.
  val Tag = 'S'

  // String - The name of the run-time parameter being reported.
  // String - The current value of the parameter.
  def decoder: Decoder[ParameterStatus] =
    (cstring ~ cstring).map(ParameterStatus(_, _))

}