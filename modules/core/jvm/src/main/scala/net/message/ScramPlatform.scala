// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.bits.ByteVector

import java.security.SecureRandom
import javax.crypto.{Mac, SecretKeyFactory}
import javax.crypto.spec.{PBEKeySpec, SecretKeySpec}

private[message] trait ScramPlatform { this: Scram.type =>

  def clientFirstBareWithRandomNonce: ByteVector = {
    val random = new SecureRandom()
    val nonceBytes = new Array[Byte](32)
    random.nextBytes(nonceBytes)
    val nonce = ByteVector.view(nonceBytes).toBase64
    clientFirstBareWithNonce(nonce)
  }

  private[message] def HMAC(key: ByteVector, str: ByteVector): ByteVector = {
    val mac = Mac.getInstance("HmacSHA256")
    val keySpec = new SecretKeySpec(key.toArray, "HmacSHA256")
    mac.init(keySpec)
    ByteVector.view(mac.doFinal(str.toArray))
  }

  private[message] def H(input: ByteVector): ByteVector =
    input.sha256

  private[message] def Hi(str: String, salt: ByteVector, iterations: Int): ByteVector = {
    val spec = new PBEKeySpec(str.toCharArray, salt.toArray, iterations, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val key = factory.generateSecret(spec)
    ByteVector.view(key.getEncoded).take(32)
  }
}
