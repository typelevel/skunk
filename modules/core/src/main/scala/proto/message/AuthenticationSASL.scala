package skunk.proto.message

import scodec.codecs.{ list, cstring }

/**
 * Specifies that SASL authentication is required. The message body is a list of SASL authentication
 * mechanisms, in the server's order of preference.
 * @param mechanisms Names of supported SASL authentication mechanisms.
 */
final case class AuthenticationSASL(mechanisms: List[String]) extends AuthenticationRequest

object AuthenticationSASL {
  final val Tagʹ = 10
  val decoderʹ = list(cstring).map(ss => AuthenticationSASL(ss.init)) // last one is always empty
}
