// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.{ IO, Resource }
import cats.implicits._
import skunk.Session
import skunk.data._
import skunk.codec.all._
import skunk.implicits._
import skunk.util.Typer
import natchez.Trace.Implicits.noop

abstract class SkunkTest(debug: Boolean = false, strategy: Typer.Strategy = Typer.Strategy.BuiltinsOnly) extends ffstest.FTest {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      strategy = strategy,
      debug    = debug
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
        _ <- assert("sanity check", n == "SkunkTest Health Check")
      } yield ()

  }

}