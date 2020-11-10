// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.Decoder
import scodec.interop.cats._

/** Specifies that the authentication was successful. */
case object AuthenticationCleartextPassword extends AuthenticationRequest {
  final val Tagʹ = 3
  val decoderʹ = AuthenticationCleartextPassword.pure[Decoder]
}
