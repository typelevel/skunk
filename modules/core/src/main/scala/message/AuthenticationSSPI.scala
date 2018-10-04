package skunk.message

import scodec.Decoder

/** Specifies that SSPI authentication is required. */
final case object AuthenticationSSPI extends AuthenticationRequest {
  final val Tagʹ = 9
  val decoderʹ = Decoder.point(AuthenticationSSPI)
}
