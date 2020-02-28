// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder

/** Specifies that the authentication was successful. */
final case object AuthenticationOk extends AuthenticationRequest {
  final val Tagʹ = 0
  val decoderʹ: Decoder[AuthenticationOk.type] = Decoder.point(AuthenticationOk)
}
