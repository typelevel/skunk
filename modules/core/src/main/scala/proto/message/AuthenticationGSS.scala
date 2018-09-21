package skunk.proto.message

import scodec.Decoder

/** Specifies that GSSAPI authentication is required. */
final case object AuthenticationGSS extends AuthenticationRequest {
  final val Tagʹ = 7
  val decoderʹ = Decoder.point(AuthenticationGSS)
}