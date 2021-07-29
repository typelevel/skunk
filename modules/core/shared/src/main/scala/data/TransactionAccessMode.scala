// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/** Enumerated type of transaction access mode values. */
sealed abstract class TransactionAccessMode(private[skunk] val sql: String) extends Product with Serializable
object TransactionAccessMode {

  case object ReadOnly extends TransactionAccessMode("READ ONLY")

  case object ReadWrite extends TransactionAccessMode("READ WRITE")
 
}
