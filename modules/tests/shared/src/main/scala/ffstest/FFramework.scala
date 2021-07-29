// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.Eq
import cats.effect._
import cats.syntax.all._
import scala.reflect.ClassTag
import natchez.Fields
import munit.CatsEffectSuite

trait FTest extends CatsEffectSuite with FTestPlatform {

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
              case fs: Fields => fs.fields // ensure .fields doesn't crash
              case _ =>
            }
          } *>
          IO(e.printStackTrace(Console.err)).whenA(show) *> e.pure[IO]
        case Left(e)    => IO.raiseError(e)
        case Right(a)   => fail[E](s"Expected ${implicitly[ClassTag[E]].runtimeClass.getName}, got $a")
      }
  }

}
