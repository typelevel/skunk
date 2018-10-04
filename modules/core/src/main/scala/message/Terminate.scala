package skunk

package message

import scodec._
import scodec.bits._

/** Termination. */
case object Terminate {

  implicit val TerminateFrontendMessage: FrontendMessage[Terminate.type] =
    FrontendMessage.tagged('X') {
      Encoder(_ => Attempt.successful(BitVector.empty))
    }

}
