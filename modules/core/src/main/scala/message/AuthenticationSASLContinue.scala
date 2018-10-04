package skunk.message

import scodec.codecs.bytes

/**
 * Specifies that this message contains a SASL challenge.
 * @param data SASL data, specific to the SASL mechanism being used.
 */
final case class AuthenticationSASLContinue(data: Array[Byte]) extends AuthenticationRequest

object AuthenticationSASLContinue {
  final val Tagʹ = 11
  val decoderʹ = bytes.map(bv => AuthenticationSASLContinue(bv.toArray))
}
