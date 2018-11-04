// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

/** Specifies that GSSAPI authentication is required. */
final case object AuthenticationGSS extends AuthenticationRequest {
  final val Tagʹ = 7
  val decoderʹ = Decoder.point(AuthenticationGSS)
}