package skunk.proto.message

import scodec.codecs._
import skunk.Type

// String
// The name of the destination prepared statement (an empty string selects the unnamed prepared statement).

// String
// The query string to be parsed.

// Int16
// The number of parameter data types specified (can be zero). Note that this is not an indication of the number of parameters that might appear in the query string, only the number that the frontend wants to prespecify types for.

// Then, for each parameter, there is the following:

// Int32
// Specifies the object ID of the parameter data type. Placing a zero here is equivalent to leaving the type unspecified.
case class Parse(name: String, sql: String, types: List[Type])

object Parse {

  implicit val ParseFrontendMessage: FrontendMessage[Parse] =
    FrontendMessage.tagged('P') {
      (utf8z ~ utf8z ~ int16 ~ list(int32)).contramap[Parse] { p =>
        p.name ~ p.sql ~ p.types.length ~ p.types.map(_.oid)
      }
    }

}