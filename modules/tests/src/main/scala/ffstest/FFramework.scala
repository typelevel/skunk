// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.Eq
import cats.effect._
import cats.implicits._
import sbt.testing.{ Framework, _ }
import sbt.testing.Status._
import scala.concurrent.ExecutionContext
import scala.Console._
import java.util.concurrent.Executors
import scala.reflect.ClassTag

trait FTest {
  protected[ffstest] var tests = List.empty[(String, IO[_])]

  private val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val ioTimer: Timer[IO] = IO.timer(ec)

  def test[A](name: String)(f: IO[A]): Unit = tests = tests :+ ((name, f))
  def fail[A](msg: String): IO[A] = IO.raiseError(new AssertionError(msg))
  def fail[A](msg: String, cause: Throwable): IO[A] = IO.raiseError(new AssertionError(msg, cause))
  def assert(msg: => String, b: => Boolean): IO[Unit] = if (b) IO.pure(()) else fail(msg)

  def assertEqual[A: Eq](msg: => String, actual: A, expected: A): IO[Unit] =
    if (expected === actual) IO.pure(())
    else fail(msg + s"\n  expected: $expected\n   actual: $actual")

  implicit class SkunkTestIOOps[A](fa: IO[A]) {
    def assertFailsWith[E: ClassTag]: IO[E] = assertFailsWith[E](false)
    def assertFailsWith[E: ClassTag](show: Boolean): IO[E] =
      fa.attempt.flatMap {
        case Left(e: E) => IO(e.printStackTrace()).whenA(show) *> e.pure[IO]
        case Left(e)    => IO.raiseError(e)
        case Right(a)   => fail[E](s"Expected ${implicitly[ClassTag[E]].runtimeClass.getName}, got $a")
      }
  }

}

class FFramework extends Framework {
  val name = "ffstest"
  val fingerprints = Array(FFingerprint: Fingerprint)
  def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
    FRunner(args, remoteArgs, testClassLoader)
}

object FFingerprint extends SubclassFingerprint {
  val isModule                = true
  val requireNoArgConstructor = true
  val superclassName: String  = classOf[FTest].getName
}

final case class FRunner(
  args:            Array[String], // unused, required by interface
  remoteArgs:      Array[String], // unused, required by interface
  testClassLoader: ClassLoader
) extends Runner {
  val done = ""
  def tasks(list: Array[TaskDef]): Array[Task] = list.map(FTask(_, testClassLoader))
}

case class FTask(taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {

  private val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val ioTimer: Timer[IO] = IO.timer(ec)

  implicit def throwableToOptionThrowable(t: Throwable): OptionalThrowable =
    new OptionalThrowable(t)

  case class FEvent(
    status:             Status,
    duration:           Long              = -1L,
    throwable:          OptionalThrowable = new OptionalThrowable() ,
    fingerprint:        Fingerprint       = taskDef.fingerprint,
    fullyQualifiedName: String            = taskDef.fullyQualifiedName,
    selector:           Selector          = taskDef.selectors.head
  ) extends Event

  def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {

    loggers.foreach(_.info(s"$YELLOW🍋  ${taskDef.fullyQualifiedName}$RESET"))

    val obj = Class.forName(taskDef.fullyQualifiedName + "$", true, testClassLoader)
      .getField("MODULE$").get(null).asInstanceOf[FTest]

    def report(color: String, message: String, event: Event): Unit = {
      loggers.foreach(_.info(s"$color   $message$RESET"))
      if (event.throwable.isDefined) {
        event.throwable.get.printStackTrace()
        loggers.foreach(_.trace(event.throwable.get))
      }
      eventHandler.handle(event)
    }

    obj.tests.parTraverse_ { case (name, fa) =>
      type AE = AssertionError // to make the lines shorter below :-\
      FTask.timed(obj.ioContextShift.shift *> fa).attempt.map {
        case Right((ms, a)) => report(GREEN, s"✓ $name ($a, $ms ms)",      FEvent(Success, duration = ms))
        case Left(e: AE)    => report(RED,   s"✗ $name (${e.getMessage})", FEvent(Failure))
        case Left(e)        => report(RED,   s"? $name (${e.getMessage})", FEvent(Error, throwable = e)) // todo: stacktrace
      }
    } .unsafeRunSync()

    // maybe we're supposed to return new tasks with new taskdefs?
    Array.empty
  }

  def tags: Array[String] = Array.empty // unused

}

object FTask {
  private val now = IO(System.currentTimeMillis)
  def timed[A](fa: IO[A]): IO[(Long, A)] = (now, fa, now).mapN((t0, a, t1) => (t1 - t0, a))
}

