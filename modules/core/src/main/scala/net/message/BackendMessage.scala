package skunk.net.message

import scodec.Decoder

/**
 * Family of messages that are received from the server (the "back end"). These messages all consist
 * of a leading tag and a length-prefixed payload. This is an open hierarchy because I don't like
 * the aesthetics of a 52-case ADT. We may revisit this but it's ok for now.
 */
trait BackendMessage

object BackendMessage {

  /**
   * Return the [un-prefixed] payload decoder for the given message tag. If the tag is unkown the
   * payload will be decoded as an `UnknownMessage`.
   */
  def decoder(tag: Byte): Decoder[BackendMessage] =
    tag match {
       case RowData.Tag               => RowData.decoder
       case AuthenticationRequest.Tag => AuthenticationRequest.decoder
       case ParameterStatus.Tag       => ParameterStatus.decoder
       case BackendKeyData.Tag        => BackendKeyData.decoder
       case ReadyForQuery.Tag         => ReadyForQuery.decoder
       case ErrorResponse.Tag         => ErrorResponse.decoder
       case RowDescription.Tag        => RowDescription.decoder
       case CommandComplete.Tag       => CommandComplete.decoder
       case NotificationResponse.Tag  => NotificationResponse.decoder
       case ParseComplete.Tag         => ParseComplete.decoder
       case ParameterDescription.Tag  => ParameterDescription.decoder
       case NoData.Tag                => NoData.decoder
       case BindComplete.Tag          => BindComplete.decoder
       case CloseComplete.Tag         => CloseComplete.decoder
       case PortalSuspended.Tag       => PortalSuspended.decoder
       case _                         => UnknownMessage.decoder(tag)
    }

}
