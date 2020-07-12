// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scala.annotation.switch
import scodec.Decoder

/**
 * Family of messages that are received from the server (the "back end"). These messages all consist
 * of a leading tag and a length-prefixed payload. This is an open hierarchy because I don't like
 * the aesthetics of a 52-case ADT. We may revisit this but it's ok for now.
 */
trait BackendMessage

object BackendMessage {

  /**
   * Return the [un-prefixed] payload decoder for the given message tag. If the tag is unknown the
   * payload will be decoded as an `UnknownMessage`.
   */
  def decoder(tag: Byte): Decoder[BackendMessage] =
    (tag: @switch) match { // N.B. `final val Tag = <char>` req'd for case switch here
       case AuthenticationRequest.Tag => AuthenticationRequest.decoder
       case BackendKeyData.Tag        => BackendKeyData.decoder
       case BindComplete.Tag          => BindComplete.decoder
       case CloseComplete.Tag         => CloseComplete.decoder
       case CommandComplete.Tag       => CommandComplete.decoder
       case CopyData.Tag              => CopyData.decoder
       case CopyDone.Tag              => CopyDone.decoder
       case CopyInResponse.Tag        => CopyInResponse.decoder
       case CopyOutResponse.Tag       => CopyOutResponse.decoder
       case EmptyQueryResponse.Tag    => EmptyQueryResponse.decoder
       case ErrorResponse.Tag         => ErrorResponse.decoder
       case NoData.Tag                => NoData.decoder
       case NotificationResponse.Tag  => NotificationResponse.decoder
       case NoticeResponse.Tag        => NoticeResponse.decoder
       case ParameterDescription.Tag  => ParameterDescription.decoder
       case ParameterStatus.Tag       => ParameterStatus.decoder
       case ParseComplete.Tag         => ParseComplete.decoder
       case PortalSuspended.Tag       => PortalSuspended.decoder
       case ReadyForQuery.Tag         => ReadyForQuery.decoder
       case RowData.Tag               => RowData.decoder
       case RowDescription.Tag        => RowDescription.decoder
       case _                         => UnknownMessage.decoder(tag)
    }

}
