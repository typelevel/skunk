// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/** Enumerated type of transaction access mode values. */
sealed abstract class TransactionAccessMode extends Product with Serializable
object TransactionAccessMode {

  case object ReadOnly extends TransactionAccessMode

  case object ReadWrite extends TransactionAccessMode

  def toLiteral(accessMode: TransactionAccessMode): String =
    accessMode match {
      case TransactionAccessMode.ReadWrite => "READ WRITE"
      case TransactionAccessMode.ReadOnly => "READ ONLY"
    }

}
