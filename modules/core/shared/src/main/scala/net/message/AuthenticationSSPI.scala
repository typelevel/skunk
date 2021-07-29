// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder

/** Specifies that SSPI authentication is required. */
case object AuthenticationSSPI extends AuthenticationRequest {
  final val Tagʹ = 9
  val decoderʹ: Decoder[AuthenticationSSPI.type] = AuthenticationSSPI.pure[Decoder]
}
