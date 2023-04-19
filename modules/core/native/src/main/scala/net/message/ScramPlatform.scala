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
    val buf = new Array[Byte](32)
    if (RAND_bytes(buf.at(0), 32) != 1)
      throw new RuntimeException("RAND_bytes")
    val nonce = ByteVector.view(buf).toBase64
    clientFirstBareWithNonce(nonce)
  }

  private[message] def HMAC(key: ByteVector, str: ByteVector): ByteVector = {
    val evpMd = EVP_get_digestbyname(c"SHA256")
    if (evpMd == null)
      throw new RuntimeException("EVP_get_digestbyname")
    val md = new Array[Byte](EVP_MAX_MD_SIZE)
    val mdLen = stackalloc[CUnsignedInt]()
    if (openssl.HMAC(evpMd, key.toArrayUnsafe.at(0), key.size.toInt, str.toArrayUnsafe.at(0), str.size.toULong, md.at(0), mdLen) == null)
      throw new RuntimeException("HMAC")
    ByteVector.view(md, 0, (!mdLen).toInt)
  }

  private[message] def H(input: ByteVector): ByteVector = {
    val md = new Array[Byte](EVP_MAX_MD_SIZE)
    val size = stackalloc[CUnsignedInt]()
    val `type` = EVP_get_digestbyname(c"SHA256")
    if (`type` == null)
      throw new RuntimeException("EVP_get_digestbyname")
    if (EVP_Digest(input.toArrayUnsafe.at(0), input.size.toULong, md.at(0), size, `type`, null) != 1)
      throw new RuntimeException("EVP_Digest")
    ByteVector.view(md, 0, (!size).toInt)
  }

  private[message] def Hi(str: String, salt: ByteVector, iterations: Int): ByteVector = {
    val digest = EVP_get_digestbyname(c"SHA256")
    if (digest == null)
      throw new RuntimeException("EVP_get_digestbyname")
    val out = new Array[Byte](32)
    if (PKCS5_PBKDF2_HMAC(str.getBytes.at(0), str.length, salt.toArrayUnsafe.at(0), salt.size.toInt, iterations, digest, 32, out.at(0)) != 1)
      throw new RuntimeException("PKCS5_PBKDF2_HMAC")
    ByteVector.view(out)
  }

}
