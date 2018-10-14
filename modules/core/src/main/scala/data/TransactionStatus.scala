package skunk.data

import cats.Eq

sealed abstract class TransactionStatus extends Product with Serializable
object TransactionStatus {

  case object Idle              extends TransactionStatus
  case object ActiveTransaction extends TransactionStatus
  case object FailedTransaction extends TransactionStatus

  implicit val eq: Eq[TransactionStatus] =
    Eq.fromUniversalEquals

}