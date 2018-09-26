package skunk
package proto
package message

import scodec.Attempt
import scodec.bits._
import scodec._

/**
 * Simple query.
 * @param sql a SQL command.
 */
case object Sync {

  implicit val SyncFrontendMessage: FrontendMessage[Sync.type] =
    FrontendMessage.tagged('S') {
      Encoder { s =>
        Attempt.Successful(BitVector.empty)
      }
    }

}