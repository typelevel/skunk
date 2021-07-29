// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message
import scodec.Encoder

// import scodec.codecs._

abstract case class PasswordMessage private[message] (password: String) extends TaggedFrontendMessage('p') {
  def encodeBody = PasswordMessage.encoder.encode(this)
}

object PasswordMessage extends PasswordMessagePlatform {

  val encoder: Encoder[PasswordMessage] =
    utf8z.contramap[PasswordMessage](_.password)

}