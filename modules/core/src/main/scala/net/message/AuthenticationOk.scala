package skunk.net.message

import scodec.Decoder

/** Specifies that the authentication was successful. */
final case object AuthenticationOk extends AuthenticationRequest {
  final val Tagʹ = 0
  val decoderʹ = Decoder.point(AuthenticationOk)
}
