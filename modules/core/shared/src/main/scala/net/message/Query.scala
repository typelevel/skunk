// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Attempt
import scodec.bits._
import scodec._

case class Query(sql: String) extends TaggedFrontendMessage('Q') {
  def encodeBody = Query.encoder.encode(this)
}

object Query {

  val encoder: Encoder[Query] =
    Encoder { q =>
      val barr  = q.sql.getBytes("UTF8")
      val barrʹ = java.util.Arrays.copyOf(barr, barr.length + 1) // add NUL
      Attempt.Successful(BitVector(barrʹ))
    }

}