package skunk.message

import scodec.codecs._

// Byte1('E')
// Identifies the message as an Execute command.

// String
// The name of the portal to execute (an empty string selects the unnamed portal).

// Int32
// Maximum number of rows to return, if portal contains a query that returns rows (ignored otherwise). Zero denotes “no limit”.

case class Execute(portal: String, maxRows: Int)

object Execute {

  implicit val ParseFrontendMessage: FrontendMessage[Execute] =
    FrontendMessage.tagged('E') {
      (utf8z ~ int32).contramap[Execute] { p =>
        p.portal ~ p.maxRows
      }
    }

}