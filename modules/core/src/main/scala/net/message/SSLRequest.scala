// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs._

case object SSLRequest {

  // SSLRequest is an untagged magic number
  implicit val SSLRequestFrontendMessage: FrontendMessage[SSLRequest.type] =
    FrontendMessage.untagged(int32.applied(80877103).contramap(_ => ()))

}