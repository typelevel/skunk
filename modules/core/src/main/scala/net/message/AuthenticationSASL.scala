// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs.{cstring, list}

/**
 * Specifies that SASL authentication is required. The message body is a list of SASL authentication
 * mechanisms, in the server's order of preference.
 * @param mechanisms Names of supported SASL authentication mechanisms.
 */
final case class AuthenticationSASL(mechanisms: List[String]) extends AuthenticationRequest

object AuthenticationSASL {
  final val Tagʹ = 10
  // last one is always empty
  val decoderʹ: Decoder[AuthenticationSASL] = list(cstring).map(ss => AuthenticationSASL(ss.init))
}
