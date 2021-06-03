// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message
import java.security.MessageDigest
import scodec.Encoder

// import scodec.codecs._

sealed abstract case class PasswordMessage(password: String) extends TaggedFrontendMessage('p') {
  def encodeBody = PasswordMessage.encoder.encode(this)
}

object PasswordMessage {

  val encoder: Encoder[PasswordMessage] =
    utf8z.contramap[PasswordMessage](_.password)

  // See https://www.postgresql.org/docs/9.6/protocol-flow.html#AEN113418
  // and https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/util/MD5Digest.java
  def md5(user: String, password: String, salt: Array[Byte]): PasswordMessage = {

    // Hash with this thing
    val md = MessageDigest.getInstance("MD5")

    // First round
    md.update(password.getBytes("UTF-8"))
    md.update(user.getBytes("UTF-8"))
    var hex = BigInt(1, md.digest).toString(16)
    while (hex.length < 32)
      hex = "0" + hex

    // Second round
    md.update(hex.getBytes("UTF-8"))
    md.update(salt)
    hex = BigInt(1, md.digest).toString(16)
    while (hex.length < 32)
      hex = "0" + hex

    // Done
    new PasswordMessage("md5" + hex) {}

  }

}