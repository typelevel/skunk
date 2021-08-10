// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder

/** Specifies that the authentication was successful. */
case object AuthenticationOk extends AuthenticationRequest {
  final val Tagʹ = 0
  val decoderʹ: Decoder[AuthenticationOk.type] = AuthenticationOk.pure[Decoder]
}
