package skunk.proto.message

import scodec.Decoder

/** Specifies that the authentication was successful. */
final case object AuthenticationCleartextPassword extends AuthenticationRequest {
  final val Tagʹ = 3
  val decoderʹ = Decoder.point(AuthenticationCleartextPassword)
}
