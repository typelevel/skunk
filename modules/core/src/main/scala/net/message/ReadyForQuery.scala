package skunk.net.message

import scodec.Decoder
import scodec.codecs._
import skunk.data.TransactionStatus

case class ReadyForQuery(status: TransactionStatus) extends BackendMessage

object ReadyForQuery {

  final val Tag = 'Z'

  def decoder: Decoder[BackendMessage] =
    byte.map {
      case 'I' => ReadyForQuery(TransactionStatus.Idle)
      case 'Z' => ReadyForQuery(TransactionStatus.ActiveTransaction)
      case 'E' => ReadyForQuery(TransactionStatus.FailedTransaction)
    }

}