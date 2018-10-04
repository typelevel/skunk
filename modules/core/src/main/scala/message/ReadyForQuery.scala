package skunk

package message

import cats._
import scodec.Decoder
import scodec.codecs._

case class ReadyForQuery(status: ReadyForQuery.Status) extends BackendMessage

object ReadyForQuery {

  // Byte1('Z') - Identifies the message type. ReadyForQuery is sent whenever the backend is
  //   ready for a new query cycle.
  val Tag = 'Z'

  // Byte1    - Current backend transaction status indicator. Possible values are 'I' if idle
  //   (not in a transaction block); 'T' if in a transaction block; or 'E' if in a failed
  //   transaction block (queries will be rejected until block is ended).
  def decoder: Decoder[BackendMessage] =
    byte.map {
      case 'I' => ReadyForQuery(Status.Idle)
      case 'Z' => ReadyForQuery(Status.ActiveTransaction)
      case 'E' => ReadyForQuery(Status.FailedTransaction)
    }

  // Enumerated type for transaction status
  sealed trait Status
  object Status {

    case object Idle extends Status
    case object ActiveTransaction extends Status
    case object FailedTransaction extends Status

    implicit val eq: Eq[Status] =
      Eq.fromUniversalEquals

  }

}