// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.bits.ByteVector

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

private[message] trait ScramPlatform { this: Scram.type =>
  
  private val crypto = js.Dynamic.global.require("crypto")

  def clientFirstBareWithRandomNonce: ByteVector = {
    val nonce = ByteVector.view(crypto.randomBytes(32).asInstanceOf[Uint8Array]).toBase64
    clientFirstBareWithNonce(nonce)
  }

  private[message] def HMAC(key: ByteVector, str: ByteVector): ByteVector = {
    val mac = crypto.createHmac("sha256", key.toUint8Array)
    mac.update(str.toUint8Array)
    ByteVector.view(mac.digest().asInstanceOf[Uint8Array])
  }

  private[message] def H(input: ByteVector): ByteVector = {
    val hash = crypto.createHash("sha256")
    hash.update(input.toUint8Array)
    ByteVector.view(hash.digest().asInstanceOf[Uint8Array])
  }

  private[message] def Hi(str: String, salt: ByteVector, iterations: Int): ByteVector = {
    // TODO It is unfortunate that we have to use a sync API here when an async is available
    // To make the change here will require running an F[_]: Async up the hiearchy    
    val salted = crypto.pbkdf2Sync(str, salt.toUint8Array, iterations, 8 * 32, "sha256")
    ByteVector.view(salted.asInstanceOf[Uint8Array]).take(32)
  }

}
