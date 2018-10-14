package skunk.net.message

import scodec.codecs.bytes

/**
 * Specifies that SASL authentication has completed.
 * @param data SASL outcome "additional data", specific to the SASL mechanism being used.
 */
final case class AuthenticationSASLFinal(data: Array[Byte]) extends AuthenticationRequest

object AuthenticationSASLFinal {
  final val Tagʹ = 12
  val decoderʹ = bytes.map(bv => AuthenticationSASLFinal(bv.toArray))
}
