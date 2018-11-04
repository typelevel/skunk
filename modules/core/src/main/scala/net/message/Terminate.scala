// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec._
import scodec.bits._

case object Terminate {

  implicit val TerminateFrontendMessage: FrontendMessage[Terminate.type] =
    FrontendMessage.tagged('X') {
      Encoder(_ => Attempt.successful(BitVector.empty))
    }

}
