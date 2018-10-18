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