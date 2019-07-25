// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs._

case class Execute(portal: String, maxRows: Int)

object Execute {

  implicit val ExecuteFrontendMessage: FrontendMessage[Execute] =
    FrontendMessage.tagged('E') {
      (utf8z ~ int32).contramap[Execute] { p =>
        p.portal ~ p.maxRows
      }
    }

}
