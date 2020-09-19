// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.codec.all._
import skunk.data.{TransactionAccessMode, TransactionIsolationLevel}
import skunk.implicits._

class TransactionAccessModeChangeTest extends SkunkTest {

  sessionTest("default") { s =>
    s.transaction.use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_READ_ONLY;".query(text))
        _ <- assert("read only transaction access mode", i == "off")
      } yield "ok"
    }
  }

  sessionTest("read only") { s =>
    s.transaction(TransactionIsolationLevel.Serializable, TransactionAccessMode.ReadOnly).use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_READ_ONLY;".query(text))
        _ <- assert("read only transaction access mode", i == "on")
      } yield "ok"
    }
  }

  sessionTest("read write") { s =>
    s.transaction(TransactionIsolationLevel.RepeatableRead, TransactionAccessMode.ReadWrite).use { _ =>
      for {
        i <- s.unique(sql"SHOW TRANSACTION_READ_ONLY;".query(text))
        _ <- assert("read write transaction access mode", i == "off")
      } yield "ok"
    }
  }

}
