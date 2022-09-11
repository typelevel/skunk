// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.bits.ByteVector

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import openssl._

private[message] trait ScramPlatform { this: Scram.type =>
  
  def clientFirstBareWithRandomNonce: ByteVector = {
    val buf = stackalloc[Byte](32)
    if (RAND_bytes(buf, 32) != 1)
      throw new RuntimeException("RAND_bytes")
    val nonce = ByteVector.view(buf, 32).toBase64
    clientFirstBareWithNonce(nonce)
  }

  private[message] def HMAC(key: ByteVector, str: ByteVector): ByteVector = Zone { implicit z =>
    val evpMd = EVP_get_digestbyname(c"SHA256")
    if (evpMd == null)
      throw new RuntimeException("EVP_get_digestbyname")
    val md = stackalloc[Byte](EVP_MAX_MD_SIZE)
    val mdLen = stackalloc[CUnsignedInt]()
    if (openssl.HMAC(evpMd, key.toPtr, key.size.toInt, str.toPtr, str.size.toULong, md, mdLen) == null)
      throw new RuntimeException("HMAC")
    ByteVector.fromPtr(md.asInstanceOf[Ptr[Byte]], (!mdLen).toLong)
  }

  private[message] def H(input: ByteVector): ByteVector = Zone { implicit z =>
    val md = stackalloc[Byte](EVP_MAX_MD_SIZE)
    val size = stackalloc[CUnsignedInt]()
    val `type` = EVP_get_digestbyname(c"SHA256")
    if (`type` == null)
      throw new RuntimeException("EVP_get_digestbyname")
    if (EVP_Digest(input.toPtr, input.size.toULong, md, size, `type`, null) != 1)
      throw new RuntimeException("EVP_Digest")
    ByteVector.fromPtr(md, (!size).toLong)
  }

  private[message] def Hi(str: String, salt: ByteVector, iterations: Int): ByteVector = Zone { implicit z =>
    val digest = EVP_get_digestbyname(c"SHA256")
    if (digest == null)
      throw new RuntimeException("EVP_get_digestbyname")
    val out = stackalloc[Byte](32)
    if (PKCS5_PBKDF2_HMAC(toCString(str), str.length, salt.toPtr, salt.size.toInt, iterations, digest, 32, out) != 1)
      throw new RuntimeException("PKCS5_PBKDF2_HMAC")
    ByteVector.fromPtr(out, 32)
  }

}
