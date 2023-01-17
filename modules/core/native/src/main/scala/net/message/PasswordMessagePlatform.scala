// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.bits.ByteVector

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import openssl._

private[message] trait PasswordMessagePlatform {
  
  // See https://www.postgresql.org/docs/9.6/protocol-flow.html#AEN113418
  // and https://github.com/pgjdbc/pgjdbc/blob/master/pgjdbc/src/main/java/org/postgresql/util/MD5Digest.java
  def md5(user: String, password: String, salt: Array[Byte]): PasswordMessage = Zone { implicit z =>

    // Hash with this thing
    val ctx = EVP_MD_CTX_new()
    if (ctx == null)
      throw new RuntimeException("EVP_MD_CTX_new")

    val `type` = EVP_get_digestbyname(c"MD5")
    if (`type` == null)
      throw new RuntimeException("EVP_get_digestbyname")

    try {

      val md = stackalloc[Byte](EVP_MAX_MD_SIZE)
      val size = stackalloc[CUnsignedInt]()

      // First round
      if (EVP_DigestInit_ex(ctx, `type`, null) != 1)
        throw new RuntimeException("EVP_DigestInit_ex")
      if (EVP_DigestUpdate(ctx, toCString(password), password.length.toULong) != 1)
        throw new RuntimeException("EVP_DigestUpdate")
      if (EVP_DigestUpdate(ctx, toCString(user), user.length.toULong) != 1)
        throw new RuntimeException("EVP_DigestUpdate")
      if (EVP_DigestFinal_ex(ctx, md, size) != 1)
        throw new RuntimeException("EVP_DigestFinal_ex")
      var hex = BigInt(1, ByteVector.view(md, (!size).toLong).toArray).toString(16)
      while (hex.length < 32)
        hex = "0" + hex

      if (EVP_MD_CTX_reset(ctx) != 1)
        throw new RuntimeException("EVP_MD_CTX_reset")

      // Second round
      if (EVP_DigestInit_ex(ctx, `type`, null) != 1)
        throw new RuntimeException("EVP_DigestInit_ex")
      if (EVP_DigestUpdate(ctx, toCString(hex), 32.toULong) != 1)
        throw new RuntimeException("EVP_DigestUpdate")
      if (EVP_DigestUpdate(ctx, ByteVector.view(salt).toPtr, salt.length.toULong) != 1)
        throw new RuntimeException("EVP_DigestUpdate")
      if (EVP_DigestFinal_ex(ctx, md, size) != 1)
        throw new RuntimeException("EVP_DigestFinal_ex")
      hex = BigInt(1, ByteVector.view(md, (!size).toLong).toArray).toString(16)
      while (hex.length < 32)
        hex = "0" + hex

      // Done
      new PasswordMessage("md5" + hex) {}

    } finally {
      openssl.EVP_MD_CTX_free(ctx)
    }

  }

}
