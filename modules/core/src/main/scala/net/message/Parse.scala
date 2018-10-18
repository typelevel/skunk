package skunk.net.message

import scodec.codecs._
import skunk.data.Type

case class Parse(name: String, sql: String, types: List[Type])

object Parse {

  implicit val ParseFrontendMessage: FrontendMessage[Parse] =
    FrontendMessage.tagged('P') {
      (utf8z ~ utf8z ~ int16 ~ list(int32)).contramap[Parse] { p =>
        p.name ~ p.sql ~ p.types.length ~ p.types.map(_.oid)
      }
    }

}