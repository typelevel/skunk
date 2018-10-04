package skunk

package message

import scodec.Attempt
import scodec.bits._
import scodec._

case object Flush {

  implicit val SyncFrontendMessage: FrontendMessage[Flush.type] =
    FrontendMessage.tagged('H') {
      Encoder { _ =>
        Attempt.Successful(BitVector.empty)
      }
    }

}