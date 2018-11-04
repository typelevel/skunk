// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

/* Specifies that an SCM credentials message is required. */
final case object AuthenticationSCMCredential extends AuthenticationRequest {
  final val Tagʹ = 6
  val decoderʹ = Decoder.point(AuthenticationSCMCredential)
}
