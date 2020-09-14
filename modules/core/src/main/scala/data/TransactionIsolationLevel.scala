// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/** Enumerated type of transaction isolation level values. */
sealed abstract class TransactionIsolationLevel(private[skunk] val sql: String) extends Product with Serializable
object TransactionIsolationLevel {

  case object Serializable extends TransactionIsolationLevel("SERIALIZABLE")

  case object RepeatableRead extends TransactionIsolationLevel("REPEATABLE READ")

  case object ReadCommitted extends TransactionIsolationLevel("READ COMMITTED")

  case object ReadUncommitted extends TransactionIsolationLevel("READ UNCOMMITTED")

}
