// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.Eq
import cats.effect._
import cats.syntax.all._
import scala.reflect.ClassTag
import munit.{CatsEffectSuite, Location, TestOptions}
import skunk.exception._
import org.typelevel.twiddles._
import org.typelevel.otel4s.trace.Tracer

trait FTest extends CatsEffectSuite with FTestPlatform {

  private def spanNameForTest(name: String): String =
    s"${getClass.getSimpleName} - $name"

  def tracedTest[A](name: String)(body: => IO[A])(implicit loc: Location): Unit =
    test(name)(Tracer[IO].span(spanNameForTest(name)).surround(body))

  def tracedTest[A](options: TestOptions)(body: => IO[A])(implicit loc: Location): Unit =
    test(options)(Tracer[IO].span(spanNameForTest(options.name)).surround(body))

  def pureTest(name: String)(f: => Boolean): Unit = test(name)(assert(name, f))
  def fail[A](msg: String): IO[A] = IO.raiseError(new AssertionError(msg))
  def fail[A](msg: String, cause: Throwable): IO[A] = IO.raiseError(new AssertionError(msg, cause))
  def assert(msg: => String, b: => Boolean): IO[Unit] = if (b) IO.pure(()) else fail(msg)

  def assertEqual[A: Eq](msg: => String, actual: A, expected: A): IO[Unit] =
    if (expected === actual) IO.pure(())
    else fail(msg + s"\n  expected: $expected\n   actual: $actual")

  implicit class SkunkTestIOOps[A](fa: IO[A]) {
    def assertFailsWith[E <: Throwable : ClassTag]: IO[E] = assertFailsWith[E](false)
    def assertFailsWith[E <: Throwable : ClassTag](show: Boolean): IO[E] =
      fa.attempt.flatMap {
        case Left(e: E) =>
          IO {
            e.toString // ensure toString doesn't crash
            e match {
              case fs: SkunkException => fs.fields // ensure .fields doesn't crash
              case _ =>
            }
          } *>
          IO(e.printStackTrace(Console.err)).whenA(show) *> e.pure[IO]
        case Left(e)    => IO.raiseError(e)
        case Right(a)   => fail[E](s"Expected ${implicitly[ClassTag[E]].runtimeClass.getName}, got $a")
      }
  }

  implicit val eqEmptyTuple: Eq[EmptyTuple] = Eq.instance((_, _) => true)
  implicit def eqTuple[A, B <: Tuple](implicit eqA: Eq[A], eqB: Eq[B]): Eq[A *: B] =
    eqA.product(eqB).contramap { case a *: b => (a, b) }
}
