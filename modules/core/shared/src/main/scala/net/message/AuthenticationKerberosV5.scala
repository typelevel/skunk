// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder

/* Specifies that Kerberos V5 authentication is required. */
case object AuthenticationKerberosV5 extends AuthenticationRequest {
  final val Tagʹ = 2
  val decoderʹ: Decoder[AuthenticationKerberosV5.type] = AuthenticationKerberosV5.pure[Decoder]
}
