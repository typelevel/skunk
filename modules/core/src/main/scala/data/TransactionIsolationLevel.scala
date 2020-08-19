// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/** Enumerated type of transaction isolation level values. */
sealed abstract class TransactionIsolationLevel extends Product with Serializable
object TransactionIsolationLevel {

  case object Serializable extends TransactionIsolationLevel

  case object RepeatableRead extends TransactionIsolationLevel

  case object ReadCommitted extends TransactionIsolationLevel

  case object ReadUncommitted extends TransactionIsolationLevel

  def toLiteral(isolationLevel: TransactionIsolationLevel): String =
    isolationLevel match {
      case TransactionIsolationLevel.Serializable => "SERIALIZABLE"
      case TransactionIsolationLevel.RepeatableRead => "REPEATABLE READ"
      case TransactionIsolationLevel.ReadCommitted => "READ COMMITTED"
      case TransactionIsolationLevel.ReadUncommitted => "READ UNCOMMITTED"
    }

}
