package skunk.net.message

import scodec.Decoder
import scodec.codecs._
import skunk.data.Notification

case class NotificationResponse(value: Notification) extends BackendMessage

object NotificationResponse {
  final val Tag = 'A'

  val decoder: Decoder[NotificationResponse] =
    (int32 ~ identifier ~ cstring).map { case pid ~ ch ~ value =>
      NotificationResponse(Notification(pid, ch, value))
    }

}
