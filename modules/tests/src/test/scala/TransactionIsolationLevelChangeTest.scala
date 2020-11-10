// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.codec.all._
import skunk.data.{TransactionAccessMode, TransactionIsolationLevel}
import skunk.implicits._

class TransactionIsolationLevelChangeTest extends SkunkTest {

  sessionTest("default") { s =>
    s.transaction.use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_ISOLATION;".query(text))
        _ <- assert("serializable transaction isolation level", i == "read committed")
      } yield "ok"
    }
  }

  sessionTest("serializable") { s =>
    s.transaction(TransactionIsolationLevel.Serializable, TransactionAccessMode.ReadOnly).use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_ISOLATION;".query(text))
        _ <- assert("serializable transaction isolation level", i == "serializable")
      } yield "ok"
    }
  }

  sessionTest("repeatable read") { s =>
    s.transaction(TransactionIsolationLevel.RepeatableRead, TransactionAccessMode.ReadOnly).use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_ISOLATION;".query(text))
        _ <- assert("repeatable read transaction isolation level", i == "repeatable read")
      } yield "ok"
    }
  }

  sessionTest("read committed") { s =>
    s.transaction(TransactionIsolationLevel.ReadCommitted, TransactionAccessMode.ReadOnly).use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_ISOLATION;".query(text))
        _ <- assert("read committed transaction isolation level", i == "read committed")
      } yield "ok"
    }
  }

  sessionTest("read uncommitted") { s =>
    s.transaction(TransactionIsolationLevel.ReadUncommitted, TransactionAccessMode.ReadOnly).use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_ISOLATION;".query(text))
        _ <- assert("read uncommitted transaction isolation level", i == "read uncommitted")
      } yield "ok"
    }
  }

}
