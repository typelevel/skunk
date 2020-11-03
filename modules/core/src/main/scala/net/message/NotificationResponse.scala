// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs._
import skunk.data.Notification

case class NotificationResponse(value: Notification[String]) extends BackendMessage

object NotificationResponse {
  final val Tag = 'A'

  val decoder: Decoder[NotificationResponse] =
    (int32 ~ identifier ~ utf8z).map { case pid ~ ch ~ value =>
      NotificationResponse(Notification(pid, ch, value))
      case _ => sys.error("dotty makes me do this but it's unpossible")
    }

}
