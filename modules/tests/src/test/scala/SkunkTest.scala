package tests

import cats.effect.{ IO, Resource }
import cats.implicits._
import skunk.Session
import skunk.data._
import skunk.codec.all._
import skunk.implicits._

trait SkunkTest extends ffstest.FTest {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  implicit class SkunkSuiteSessionOps(s: Session[IO]) {

    def assertTransactionStatus(msg: String, xas: TransactionStatus): IO[Unit] =
      s.transactionStatus.get.flatMap(a => assert(s"$msg (expected $xas, got $a)", a === xas))

    def assertHealthy: IO[Unit] =
      for {
        _ <- assertTransactionStatus("sanity check", TransactionStatus.Idle)
        n <- s.execute(sql"select 42".query(int4))
        _ <- assert("sanity check", n === List(42))
      } yield ()

  }

  implicit class SkunkSuiteIOOps(fa: IO[_]) {
    def assertFailsWithSqlException: IO[Unit] =
      fa.attempt.flatMap {
        case Left(_: SqlException) => IO.unit
        case Left(e)               => IO.raiseError(new RuntimeException("Expected SqlException, got another exception (see cause).", e))
        case Right(a)              => IO.raiseError(new RuntimeException(s"Expected SqlException, got $a"))
      }
  }

  def sessionTest[A](name: String)(fa: Session[IO] => IO[A]): Unit =
    test(name)(session.use(fa))

}