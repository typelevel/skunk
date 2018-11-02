package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.data.TransactionStatus._
import utest.{ assert => _, _ }

object TransactionStatusSuite extends SkunkSuite {

  val tests = Tests {

    'simple - {

      'nonTransactional - unsafeRunSession { s =>
        for {
          _ <- s.assertTransactionStatus("initial state", Idle)
          _ <- s.execute(sql"select 42".query(int4))
          _ <- s.assertTransactionStatus("after successful query.", Idle)
          _ <- s.execute(sql"select !".query(int4)).assertFailsWithSqlException
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
          _ <- s.execute(sql"foo?".command).assertFailsWithSqlException
          _ <- s.assertTransactionStatus("after error", FailedTransaction)
          _ <- s.execute(sql"rollback".command)
          _ <- s.assertTransactionStatus("after rollback", Idle)
        } yield "ok"

      }

    }

  }
}

