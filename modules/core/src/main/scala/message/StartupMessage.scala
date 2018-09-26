package skunk
package proto
package message

import scodec._
import scodec.codecs._

// TODO: SUPPORT OTHER PARAMETERS
case class StartupMessage(user: String, database: String)

object StartupMessage {

  // StartupMessage (F)
  //
  // Int32(196608) - The protocol version number. The most significant 16 bits are the major
  //   version number (3 for the protocol described here). The least significant 16 bits are the
  //   minor version number (0 for the protocol described here).
  //
  // The protocol version number is followed by one or more pairs of parameter name and value strings.
  // A zero byte is required as a terminator after the last name/value pair. Parameters can appear in
  // any order. user is required, others are optional. Each parameter is specified as:
  //
  // String - The parameter name. Currently recognized names are:
  //
  //    * user     - The database user name to connect as. Required; there is no default.
  //    * database - The database to connect to. Defaults to the user name.
  //    * options  - Command-line arguments for the backend. (This is deprecated in favor of setting
  //                 individual run-time parameters.
  //
  //   In addition to the above, any run-time parameter that can be set at backend start time might
  //   Be listed. Such settings will be applied during backend start (after parsing the command-line
  //   options if any). The values will act as session defaults.
  //
  // String - The parameter value.

  implicit val StartupMessageFrontendMessage: FrontendMessage[StartupMessage] =
    FrontendMessage.untagged {

      val version: Codec[Unit] =
        int32.applied(196608)

      def pair(key: String): Codec[String] =
        cstring.applied(key) ~> cstring

      (version ~> pair("user") ~ pair("database") <~ byte.applied(0))
        .asEncoder
        .contramap(m => m.user ~ m.database)

    }

}