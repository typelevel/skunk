// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.{ IO, Resource }
import cats.syntax.all.*
import skunk.{Command, Session, TypingStrategy, Void}
import skunk.data.*
import skunk.codec.all.*
import skunk.implicits.*
import munit.Location
import org.typelevel.otel4s.trace.Tracer
import scala.concurrent.duration.Duration

abstract class SkunkTest(debug: Boolean = false, typingStrategy: TypingStrategy = TypingStrategy.BuiltinsOnly) extends ffstest.FTest {

  def session(implicit tracer: Tracer[IO]): Resource[IO, Session[IO]] = session(Duration.Inf)

  def session(readTimeout: Duration)(implicit tracer: Tracer[IO]): Resource[IO, Session[IO]] =
    Session.Builder[IO]
      .withUserAndPassword("jimmy", "banana")
      .withDatabase("world")
      .withTypingStrategy(typingStrategy)
      .withDebug(debug)
      .withReadTimeout(readTimeout)
      .single

  def sessionTest[A](name: String, readTimeout: Duration = Duration.Inf)(fa: Session[IO] => IO[A])(implicit loc: Location): Unit =
    tracedTest(name)(tracer => session(readTimeout)(tracer).use(fa))

  def sessionTestWithCleanup[A](name: String,
                                readTimeout: Duration = Duration.Inf)
                               (fa: Session[IO] => IO[A])
                               (cleanup: Command[Void]*)
                               (implicit loc: Location): Unit =
    tracedTest(name) {
      session(readTimeout)(_).use { s =>
        (fa(s) >> s.assertHealthy)
          .guarantee(cleanup.toList.traverse_(s.execute))
      }
    }

  def pooled(max: Int = 8, readTimeout: Duration = Duration.Inf, parseCacheSize: Int = 1024)(implicit tracer: Tracer[IO]): Resource[IO, Resource[IO, Session[IO]]] =
    Session.Builder[IO]
      .withUserAndPassword("jimmy", "banana")
      .withDatabase("world")
      .withTypingStrategy(typingStrategy)
      .withDebug(debug)
      .withReadTimeout(readTimeout)
      .withParseCacheSize(parseCacheSize)
      .pooled(max)

  def pooledTest[A](name: String, max: Int = 8, readTimeout: Duration = Duration.Inf, parseCacheSize: Int = 1024)(fa: Resource[IO, Session[IO]] => IO[A])(implicit loc: Location): Unit =
    tracedTest(name)(tracer => pooled(max, readTimeout, parseCacheSize)(tracer).use(fa))

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
