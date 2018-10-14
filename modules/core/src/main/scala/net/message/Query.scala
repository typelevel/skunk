package skunk.net.message

import java.util.Arrays
import scodec.Attempt
import scodec.bits._
import scodec._

/**
 * Simple query.
 * @param sql a SQL command.
 */
case class Query(sql: String)

object Query {

  implicit val QueryFrontendMessage: FrontendMessage[Query] =
    FrontendMessage.tagged('Q') {
      Encoder { q =>
        val barr  = q.sql.getBytes("UTF8")
        val barrʹ = Arrays.copyOf(barr, barr.length + 1) // add NUL
        Attempt.Successful(BitVector(barrʹ))
      }
    }

}