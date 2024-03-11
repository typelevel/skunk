// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs.bytes

/**
 * Specifies that an MD5-encrypted password is required.
 * @param salt The salt to use when encrypting the password.
 */
final case class AuthenticationMD5Password(salt: Array[Byte]) extends AuthenticationRequest

object AuthenticationMD5Password {
  final val Tagʹ = 5
  val decoderʹ: Decoder[AuthenticationMD5Password] = bytes(4).map(bs => AuthenticationMD5Password(bs.toArray))
}
