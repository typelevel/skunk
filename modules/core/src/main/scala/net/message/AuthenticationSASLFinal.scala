// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.bits.ByteVector
import scodec.codecs.bytes


/**
 * Specifies that SASL authentication has completed.
 * @param data SASL outcome "additional data", specific to the SASL mechanism being used.
 */
final case class AuthenticationSASLFinal(data: ByteVector) extends AuthenticationRequest

object AuthenticationSASLFinal {
  final val Tagʹ = 12
  val decoderʹ: Decoder[AuthenticationSASLFinal] = bytes.map(bv => AuthenticationSASLFinal(bv))
}
