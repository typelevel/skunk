package skunk.net.message

import scodec.Attempt
import scodec.bits._
import scodec._

case object Sync {

  implicit val SyncFrontendMessage: FrontendMessage[Sync.type] =
    FrontendMessage.tagged('S') {
      Encoder { _ =>
        Attempt.Successful(BitVector.empty)
      }
    }

}