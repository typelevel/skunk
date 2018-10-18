package skunk.net.message

import scodec._
import scodec.codecs._

// TODO: SUPPORT OTHER PARAMETERS
case class StartupMessage(user: String, database: String)

object StartupMessage {

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