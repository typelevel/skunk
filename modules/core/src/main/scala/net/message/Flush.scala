// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Attempt
import scodec.bits._
import scodec._

case object Flush {

  implicit val FlushFrontendMessage: FrontendMessage[Flush.type] =
    FrontendMessage.tagged('H') {
      Encoder { _ =>
        Attempt.Successful(BitVector.empty)
      }
    }

}