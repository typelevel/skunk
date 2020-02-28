// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs.bytes

/**
 * Specifies that this message contains GSSAPI or SSPI data.
 * @param data GSSAPI or SSPI authentication data.
 */
final case class AuthenticationGSSContinue(data: Array[Byte]) extends AuthenticationRequest

object AuthenticationGSSContinue {
  final val Tagʹ = 8
  val decoderʹ: Decoder[AuthenticationGSSContinue] = bytes.map(bv => AuthenticationGSSContinue(bv.toArray))
}
