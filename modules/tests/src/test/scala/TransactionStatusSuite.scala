package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.data.TransactionStatus._
import utest.{ assert => _, _ }

/**
 * Every query/command cycle ends with a `ReadyForQuery` message that indicates the current
 * transaction status (idle, active, errored). We need to ensure that our `Signal` reflects reality,
 * and that we recover our grip on reality after an `ErrorResponse` puts us in a weird state.
 */
object TransactionStatusSuite extends SkunkSuite {

  val tests = Tests {

    'simple - {

      'nonTransactional - unsafeRunSession { s =>
        for {
          _ <- s.assertTransactionStatus("initial state", Idle)
          _ <- s.execute(sql"select 42".query(int4))
          _ <- s.assertTransactionStatus("after successful query.", Idle)
          _ <- s.execute(sql"select !".query(int4)).assertFailsWithSqlException // ensure recovery
          _ <- s.assertTransactionStatus("after failure.", Idle)
        } yield "ok"
      }

      'transactional - unsafeRunSession { s =>
        for {
          _ <- s.assertTransactionStatus("initial state", Idle)
          _ <- s.execute(sql"begin".command)
          _ <- s.assertTransactionStatus("after begin", ActiveTransaction)
          _ <- s.execute(sql"commit".command)
          _ <- s.assertTransactionStatus("after commit", Idle)
          _ <- s.execute(sql"begin".command)
          _ <- s.assertTransactionStatus("after begin", ActiveTransaction)
          _ <- s.execute(sql"rollback".command)
          _ <- s.assertTransactionStatus("after rollback", Idle)
          _ <- s.execute(sql"begin".command)
          _ <- s.assertTransactionStatus("after begin", ActiveTransaction)
          _ <- s.execute(sql"foo?".command).assertFailsWithSqlException // ensure recovery
          _ <- s.assertTransactionStatus("after error", FailedTransaction)
          _ <- s.execute(sql"rollback".command)
          _ <- s.assertTransactionStatus("after rollback", Idle)
        } yield "ok"

      }

    }

  }
}

