package skunk.proto.message

import scodec.codecs._

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
case class NotificationResponse(pid: Int, channel: String, payload: String) extends BackendMessage

object NotificationResponse {
  final val Tag = 'A'
  val decoder = (int32 ~ cstring ~ cstring).map(NotificationResponse(_, _, _))
}
