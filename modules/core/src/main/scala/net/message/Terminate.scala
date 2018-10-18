package skunk.net.message

import scodec._
import scodec.bits._

case object Terminate {

  implicit val TerminateFrontendMessage: FrontendMessage[Terminate.type] =
    FrontendMessage.tagged('X') {
      Encoder(_ => Attempt.successful(BitVector.empty))
    }

}
