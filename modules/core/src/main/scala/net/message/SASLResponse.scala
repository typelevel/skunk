// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Encoder
import scodec.bits.ByteVector
import scodec.codecs.bytes

/**
 * Non-initial response from client to server in a SASL authentication.
 * @param response Mechanism specific response message.
 */
final case class SASLResponse(response: ByteVector) extends TaggedFrontendMessage('p') {
  protected def encodeBody = SASLResponse.encoder.encode(this)

  override def toString = s"SASLResponse($response)"
}

object SASLResponse {
  val encoder: Encoder[SASLResponse] =
    bytes.contramap[SASLResponse](_.response)
}
