package skunk.net.message

import scodec.codecs._
import skunk.data.Notification

// NotificationResponse (B)
// Byte1('A')
// Identifies the message as a notification response.

// Int32
// Length of message contents in bytes, including self.

// Int32
// The process ID of the notifying backend process.

// String
// The name of the channel that the notify has been raised on.

// String
// The “payload” string passed from the notifying process.
case class NotificationResponse(value: Notification) extends BackendMessage

object NotificationResponse {
  final val Tag = 'A'
  val decoder = (int32 ~ identifier ~ cstring).map { case pid ~ ch ~ value =>
    NotificationResponse(Notification(pid, ch, value))
  }
}
