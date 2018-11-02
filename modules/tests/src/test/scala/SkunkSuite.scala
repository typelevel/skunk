package tests

import cats.effect._
import cats.implicits._
import scala.concurrent.ExecutionContext
import skunk._
import skunk.codec.all._
import skunk.implicits._
import skunk.data._
import utest.{ assert => _, _ }

trait SkunkSuite extends TestSuite {

  implicit val ioContextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit val ioTimer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  def unsafeRunSession[A](f: Session[IO] => IO[A]): A =
    session.use(f).unsafeRunSync

  def assert(msg: => String, b: => Boolean): IO[Unit] =
    if (b) IO.pure(())
    else IO.raiseError(new RuntimeException(s"Assertion failed: $msg"))

  implicit class SessionOps(s: Session[IO]) {

    def assertTransactionStatus(msg: String, xas: TransactionStatus): IO[Unit] =
      s.transactionStatus.get.flatMap(a => assert(s"$msg (expected $xas, got $a)", a === xas))

    def assertHealthy: IO[Unit] =
      for {
        _ <- assertTransactionStatus("sanity check", TransactionStatus.Idle)
        n <- s.execute(sql"select 42".query(int4))
        _ <- assert("sanity check", n === List(42))
      } yield ()


  }

  implicit class ErrorResponseSuiteIOOps(fa: IO[_]) {
    def assertFailsWithSqlException: IO[Unit] =
      fa.attempt.flatMap {
        case Left(_: SqlException) => IO.unit
        case Left(e)               => IO.raiseError(new RuntimeException("Expected SqlException, got another exception (see cause).", e))
        case Right(a)              => IO.raiseError(new RuntimeException(s"Expected SqlException, got $a"))
      }
  }


}
