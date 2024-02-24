// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.Eq

/** Enumerated type of transaction status values. See the companion object for more information. */
sealed abstract class TransactionStatus extends Product with Serializable
object TransactionStatus {

  /** No current transaction. */
  case object Idle extends TransactionStatus

  /** Transaction is active has not encountered an error. */
  case object Active extends TransactionStatus

  /** Transaction has encountered an error and must be rolled back. */
  case object Failed extends TransactionStatus

  implicit val eq: Eq[TransactionStatus] =
    Eq.fromUniversalEquals

}