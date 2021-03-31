// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs._

final case class ErrorResponse(info: Map[Char, String]) extends BackendMessage {
  override def toString: String =
    info.map { case (k, v) => s"$k -> $v" } .mkString("ErrorResponse(", ", ", "")
}

object ErrorResponse {

  // ErrorResponse (B)
  final val Tag = 'E'

  // The message body consists of one or more identified fields, followed by a zero byte as a
  // terminator. Fields can appear in any order. For each field there is the following:
  //
  // Byte1  - A code identifying the field type; if zero, this is the message terminator and no
  //          string follows. The presently defined field types are listed in Section 48.6. Since
  //          more field types might be added in future, frontends should silently ignore fields of
  //          unrecognized type.
  // String - The field value.
  val decoder: Decoder[BackendMessage] =
    list(utf8z).map { ss =>
      val kv = ss.init.map(s => s.head -> s.tail).toMap // last one is always empty
      ErrorResponse(kv)
    }

}