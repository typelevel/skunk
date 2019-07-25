// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Attempt
import scodec.bits._
import scodec._

case class Query(sql: String)

object Query {

  implicit val QueryFrontendMessage: FrontendMessage[Query] =
    FrontendMessage.tagged('Q') {
      Encoder { q =>
        val barr  = q.sql.getBytes("UTF8")
        val barrʹ = java.util.Arrays.copyOf(barr, barr.length + 1) // add NUL
        Attempt.Successful(BitVector(barrʹ))
      }
    }

}