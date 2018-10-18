package skunk.net.message

import scodec.codecs._

case class ParameterStatus(name: String, value: String) extends BackendMessage

object ParameterStatus {
  final val Tag = 'S'
  def decoder = (cstring ~ cstring).map(apply)
}