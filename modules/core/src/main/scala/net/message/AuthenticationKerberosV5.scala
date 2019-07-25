// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

/* Specifies that Kerberos V5 authentication is required. */
case object AuthenticationKerberosV5 extends AuthenticationRequest {
  final val Tagʹ = 2
  val decoderʹ: Decoder[AuthenticationKerberosV5.type] = Decoder.point(AuthenticationKerberosV5)
}
