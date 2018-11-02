package tests

import cats._
import cats.effect._
import cats.implicits._
import scala.concurrent.ExecutionContext
import skunk._
import skunk.implicits._
import skunk.codec.all._
import skunk.data._
import utest.{ assert => _, _ }

trait SkunkSuite extends TestSuite {

  implicit val IOContextSwitch =

  IO.contextShift(ExecutionContext.global)

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  def assert(msg: => String, b: => Boolean): IO[Unit] =
    if (b) IO.pure(())
    else IO.raiseError(new RuntimeException(s"Assertion failed: $msg"))

  implicit class SessionOps(s: Session[IO]) {
    def assertTransactionStatus(msg: String, xas: TransactionStatus): IO[Unit] =
      s.transactionStatus.get.flatMap(a => assert(s"$msg (expected $xas, got $a)", a === xas))
  }

}

object SessionSuite extends SkunkSuite {

  val tests = Tests {
    import TransactionStatus._

    'transactionStatus - {

      'simple - {
        session.use { s =>
          for {
            _   <- s.assertTransactionStatus("initial state", Idle)
            _   <- s.execute(sql"select 42".query(int4))
            _   <- s.assertTransactionStatus("after successful query.", Idle)
            _   <- s.execute(sql"select !".query(int4)).attempt
            _   <- s.assertTransactionStatus("after failure.", Idle)
          } yield "ok"
        } .unsafeRunSync
      }

      'extended - {
        session.use { s =>
          for {
            _   <- s.assertTransactionStatus("initial state", Idle)
            _   <- s.execute(sql"begin".command)
            _   <- s.assertTransactionStatus("after begin", ActiveTransaction)
            _   <- s.execute(sql"commit".command)
            _   <- s.assertTransactionStatus("after commit", Idle)
          } yield "ok"

        // 'activeAfterBegin - 1
        // 'idleAfterCommit - 1
        // 'idleAfterRollback - 1
        // 'errorAfterError - 1
        } .unsafeRunSync
      }

    }
  }

}
