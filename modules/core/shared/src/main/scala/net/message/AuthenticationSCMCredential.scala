// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder

/* Specifies that an SCM credentials message is required. */
case object AuthenticationSCMCredential extends AuthenticationRequest {
  final val Tagʹ = 6
  val decoderʹ: Decoder[AuthenticationSCMCredential.type] = AuthenticationSCMCredential.pure[Decoder]
}
