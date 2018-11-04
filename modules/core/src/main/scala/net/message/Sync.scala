// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

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