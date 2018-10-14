package skunk.net.message

import scodec.Decoder
import scodec.codecs._
import skunk.data.TransactionStatus

case class ReadyForQuery(status: TransactionStatus) extends BackendMessage

object ReadyForQuery {

  // Byte1('Z') - Identifies the message type. ReadyForQuery is sent whenever the backend is
  //   ready for a new query cycle.
  val Tag = 'Z'

  // Byte1    - Current backend transaction status indicator. Possible values are 'I' if idle
  //   (not in a transaction block); 'T' if in a transaction block; or 'E' if in a failed
  //   transaction block (queries will be rejected until block is ended).
  def decoder: Decoder[BackendMessage] =
    byte.map {
      case 'I' => ReadyForQuery(TransactionStatus.Idle)
      case 'Z' => ReadyForQuery(TransactionStatus.ActiveTransaction)
      case 'E' => ReadyForQuery(TransactionStatus.FailedTransaction)
    }

}