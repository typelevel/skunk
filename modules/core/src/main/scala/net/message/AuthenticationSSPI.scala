// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

/** Specifies that SSPI authentication is required. */
case object AuthenticationSSPI extends AuthenticationRequest {
  final val Tagʹ = 9
  val decoderʹ: Decoder[AuthenticationSSPI.type] = Decoder.point(AuthenticationSSPI)
}
