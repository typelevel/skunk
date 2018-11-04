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
      check    = false
    )

  def sessionTest[A](name: String)(fa: Session[IO] => IO[A]): Unit =
    test(name)(session.use(fa))

  implicit class SkunkTestSessionOps(s: Session[IO]) {

    def assertTransactionStatus(msg: String, xas: TransactionStatus): IO[Unit] =
      s.transactionStatus.get.flatMap(a => assert(s"$msg (expected $xas, got $a)", a === xas))

    def assertHealthy: IO[Unit] =
      for {
        _ <- assertTransactionStatus("sanity check", TransactionStatus.Idle)
        n <- s.execute(sql"select 42".query(int4))
        _ <- assert("sanity check", n === List(42))
      } yield ()

  }

  implicit class SkunkTestIOOps[A](fa: IO[A]) {
    type SE = SqlException
    def assertFailsWithSqlException: IO[Unit] =
      fa.attempt.flatMap {
        case Left(_: SE) => IO.unit
        case Left(e)     => IO.raiseError(e)
        case Right(a)    => fail(s"Expected SqlException, got $a")
      }
  }


}