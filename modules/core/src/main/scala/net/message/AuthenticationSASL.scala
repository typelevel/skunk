// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs.list

/**
 * Specifies that SASL authentication is required. The message body is a list of SASL authentication
 * mechanisms, in the server's order of preference.
 * @param mechanisms Names of supported SASL authentication mechanisms.
 */
final case class AuthenticationSASL(mechanisms: List[String]) extends AuthenticationRequest {
  override def toString = s"AuthenticationSASL(${mechanisms.mkString(", ")})"
}

object AuthenticationSASL {
  final val Tagʹ = 10
  val decoderʹ = list(utf8z).map(ss => AuthenticationSASL(ss.init)) // last one is always empty
}
