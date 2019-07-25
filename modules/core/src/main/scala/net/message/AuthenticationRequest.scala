// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scala.annotation.switch
import scodec.Decoder
import scodec.codecs.int32

/**
 * Family of `BackendMessage` relating to authentication. All share the same top-level decoder,
 * which switches on a secondary tag and delegates to secondary decoders.
 */
trait AuthenticationRequest extends BackendMessage

object AuthenticationRequest {
  final val Tag = 'R'
  val decoder: Decoder[AuthenticationRequest] =
    int32.flatMap { a =>
      (a: @switch) match {
        case AuthenticationOk.Tagʹ                => AuthenticationOk.decoderʹ
        case AuthenticationKerberosV5.Tagʹ        => AuthenticationKerberosV5.decoderʹ
        case AuthenticationCleartextPassword.Tagʹ => AuthenticationCleartextPassword.decoderʹ
        case AuthenticationMD5Password.Tagʹ       => AuthenticationMD5Password.decoderʹ
        case AuthenticationSCMCredential.Tagʹ     => AuthenticationSCMCredential.decoderʹ
        case AuthenticationGSS.Tagʹ               => AuthenticationGSS.decoderʹ
        case AuthenticationSSPI.Tagʹ              => AuthenticationSSPI.decoderʹ
        case AuthenticationGSSContinue.Tagʹ       => AuthenticationGSSContinue.decoderʹ
        case AuthenticationSASL.Tagʹ              => AuthenticationSASL.decoderʹ
        case AuthenticationSASLContinue.Tagʹ      => AuthenticationSASLContinue.decoderʹ
        case AuthenticationSASLFinal.Tagʹ         => AuthenticationSASLFinal.decoderʹ
      }
    }
}
