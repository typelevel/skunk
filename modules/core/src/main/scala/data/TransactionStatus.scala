// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

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