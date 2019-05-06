// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.{ IO, Resource }
import cats.implicits._
import scala.reflect.ClassTag
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
      // debug = true
    )

  def sessionTest[A](name: String)(fa: Session[IO] => IO[A]): Unit =
    test(name)(session.use(fa))

  implicit class SkunkTestSessionOps(s: Session[IO]) {

    def assertTransactionStatus(msg: String, xas: TransactionStatus): IO[Unit] =
      s.transactionStatus.get.flatMap(a => assert(s"$msg (expected $xas, got $a)", a === xas))

    def assertHealthy: IO[Unit] =
      for {
        _ <- assertTransactionStatus("sanity check", TransactionStatus.Idle)
        n <- s.unique(sql"select 'SkunkTest Health Check'::varchar".query(varchar))
        _ <- assert("sanity check", n === "SkunkTest Health Check")
      } yield ()

  }

  implicit class SkunkTestIOOps[A](fa: IO[A]) {
    def assertFailsWith[E: ClassTag]: IO[E] = assertFailsWith[E](false)
    def assertFailsWith[E: ClassTag](show: Boolean): IO[E] =
      fa.attempt.flatMap {
        case Left(e: E) => IO(e.printStackTrace()).whenA(show) *> e.pure[IO]
        case Left(e)    => IO.raiseError(e)
        case Right(a)   => fail[E](s"Expected SqlException, got $a")
      }
  }

}