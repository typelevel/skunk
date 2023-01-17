// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scala.scalajs.js
import scala.scalajs.js.typedarray._
import scodec.bits.ByteVector

private[message] trait PasswordMessagePlatform {
  
  private val crypto = js.Dynamic.global.require("crypto")

  // See https://www.postgresql.org/docs/9.6/protocol-flow.html#AEN113418
  // and https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/util/MD5Digest.java
  def md5(user: String, password: String, salt: Array[Byte]): PasswordMessage = {

    // Hash with this thing
    var md = crypto.createHash("md5")

    // First round
    md.update(password)
    md.update(user)
    var hex = BigInt(1, ByteVector.view(md.digest().asInstanceOf[Uint8Array]).toArray).toString(16)
    while (hex.length < 32)
      hex = "0" + hex

    // Second round
    md = crypto.createHash("md5")
    md.update(hex)
    md.update(salt.toTypedArray)
    hex = BigInt(1, ByteVector.view(md.digest().asInstanceOf[Uint8Array]).toArray).toString(16)
    while (hex.length < 32)
      hex = "0" + hex

    // Done
    new PasswordMessage("md5" + hex) {}

  }

}
