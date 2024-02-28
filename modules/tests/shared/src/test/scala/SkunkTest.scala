// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import skunk.Session
import skunk.data._
import skunk.codec.all._
import skunk.implicits._
import skunk.util.Typer
import munit.Location
import org.typelevel.otel4s.trace.Tracer
import scala.concurrent.duration.Duration

abstract class SkunkTest(debug: Boolean = false, strategy: Typer.Strategy = Typer.Strategy.BuiltinsOnly) extends ffstest.FTest {

  def session(implicit tracer: Tracer[IO]): Resource[IO, Session[IO]] = session(Duration.Inf)

  def session(readTimeout: Duration)(implicit tracer: Tracer[IO]): Resource[IO, Session[IO]] =
    Session.single(
      host        = "localhost",
      port        = 5432,
      user        = "jimmy",
      database    = "world",
      password    = Some("banana"),
      strategy    = strategy,
      debug       = debug,
      readTimeout = readTimeout
    )

  def sessionTest[A](name: String, readTimeout: Duration = Duration.Inf)(fa: Session[IO] => IO[A])(implicit loc: Location): Unit =
    tracedTest(name)(tracer => session(readTimeout)(tracer).use(fa))

  def pooled(max: Int = 8, readTimeout: Duration = Duration.Inf)(implicit tracer: Tracer[IO]): Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled(
      host        = "localhost",
      port        = 5432,
      user        = "jimmy",
      database    = "world",
      password    = Some("banana"),
      max         = max,
      strategy    = strategy,
      debug       = debug,
      readTimeout = readTimeout
    )

  def pooledTest[A](name: String, max: Int = 8, readTimeout: Duration = Duration.Inf)(fa: Resource[IO, Session[IO]] => IO[A])(implicit loc: Location): Unit =
    tracedTest(name)(tracer => pooled(max, readTimeout)(tracer).use(fa))

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
