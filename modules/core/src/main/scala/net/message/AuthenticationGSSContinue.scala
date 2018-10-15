package skunk.net.message

import scodec.codecs.bytes

/**
 * Specifies that this message contains GSSAPI or SSPI data.
 * @param data GSSAPI or SSPI authentication data.
 */
final case class AuthenticationGSSContinue(data: Array[Byte]) extends AuthenticationRequest

object AuthenticationGSSContinue {
  final val Tagʹ = 8
  val decoderʹ = bytes.map(bv => AuthenticationGSSContinue(bv.toArray))
}
