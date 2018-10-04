package skunk.message

import scodec.Decoder

/* Specifies that Kerberos V5 authentication is required. */
final case object AuthenticationKerberosV5 extends AuthenticationRequest {
  final val Tagʹ = 2
  val decoderʹ = Decoder.point(AuthenticationKerberosV5)
}
