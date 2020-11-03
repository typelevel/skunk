// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.data.TransactionStatus._
import skunk.exception.PostgresErrorException

/**
 * Every query/command cycle ends with a `ReadyForQuery` message that indicates the current
 * transaction status (idle, active, errored). We need to ensure that our `Signal` reflects reality,
 * and that we recover our grip on reality after an `ErrorResponse` puts us in a weird state.
 */
class TransactionStatusTest extends SkunkTest {

  sessionTest("simple, non-transactional") { s =>
    for {
      _ <- s.assertTransactionStatus("initial state", Idle)
      _ <- s.execute(sql"select 42".query(int4))
      _ <- s.assertTransactionStatus("after successful query.", Idle)
      _ <- s.execute(sql"select !".query(int4)).assertFailsWith[PostgresErrorException] // ensure recovery
      _ <- s.assertTransactionStatus("after failure.", Idle)
    } yield "ok"
  }

  sessionTest("simple, transactional") { s =>
    for {
      _ <- s.assertTransactionStatus("initial state", Idle)
      _ <- s.execute(sql"begin".command)
      _ <- s.assertTransactionStatus("after begin", Active)
      _ <- s.execute(sql"commit".command)
      _ <- s.assertTransactionStatus("after commit", Idle)
      _ <- s.execute(sql"begin".command)
      _ <- s.assertTransactionStatus("after begin", Active)
      _ <- s.execute(sql"rollback".command)
      _ <- s.assertTransactionStatus("after rollback", Idle)
      _ <- s.execute(sql"begin".command)
      _ <- s.assertTransactionStatus("after begin", Active)
      _ <- s.execute(sql"foo?".command).assertFailsWith[PostgresErrorException] // ensure recovery
      _ <- s.assertTransactionStatus("after error", Failed)
      _ <- s.execute(sql"rollback".command)
      _ <- s.assertTransactionStatus("after rollback", Idle)
    } yield "ok"
  }

}

