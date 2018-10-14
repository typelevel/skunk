package skunk.net.message

import scodec.Decoder

/* Specifies that an SCM credentials message is required. */
final case object AuthenticationSCMCredential extends AuthenticationRequest {
  final val Tagʹ = 6
  val decoderʹ = Decoder.point(AuthenticationSCMCredential)
}
