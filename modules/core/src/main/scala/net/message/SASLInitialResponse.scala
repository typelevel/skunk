// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Encoder
import scodec.bits.ByteVector
import scodec.codecs.{bytes, int32, variableSizeBytes}

/**
 * Initial response from client to server in a SASL authentication. The supplied mechanism
 * is one of the advertised mechanisms from the `AuthenticationSASL` message.
 * @param mechanism Names of selected SASL authentication mechanism.
 * @param initialResponse Mechanism specific response message.
 */
final case class SASLInitialResponse(mechanism: String, initialResponse: ByteVector) extends TaggedFrontendMessage('p') {
  protected def encodeBody = SASLInitialResponse.encoder.encode(this)

  override def toString = s"SASLInitialResponse($mechanism, $initialResponse)"
}

object SASLInitialResponse {
  val encoder: Encoder[SASLInitialResponse] =
    (utf8z ~ variableSizeBytes(int32, bytes)).contramap[SASLInitialResponse] { r => (r.mechanism, r.initialResponse) }
}
