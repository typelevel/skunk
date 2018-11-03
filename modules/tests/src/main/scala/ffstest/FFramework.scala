package ffstest

import cats.effect._
import cats.implicits._
import sbt.testing.{ Framework, _ }
import sbt.testing.Status._
import scala.concurrent.ExecutionContext
import scala.Console._

trait FTest {
  protected[ffstest] var tests = List.empty[(String, IO[_])]
  implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val ioTimer: Timer[IO] = IO.timer(ExecutionContext.global)
  def test[A](name: String)(f: IO[A]) = tests = tests :+ ((name, f))
  def assert(msg: => String, b: => Boolean): IO[Unit] =
    if (b) IO.pure(()) else IO.raiseError(new AssertionError(msg))
}

class FFramework extends Framework {
  val name = "ffstest"
  val fingerprints = Array(FFingerprint : Fingerprint)
  def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
    new FRunner(args, remoteArgs, testClassLoader)
}

object FFingerprint extends SubclassFingerprint {
  val isModule                = true
  val requireNoArgConstructor = true
  val superclassName          = classOf[FTest].getName
}

final case class FRunner(
  args:            Array[String], // unused, required by interface
  remoteArgs:      Array[String], // unused, required by interface
  testClassLoader: ClassLoader
) extends Runner {
  val done = ""
  def tasks(list: Array[TaskDef]) = list.map(FTask(_, testClassLoader))
}

case class FTask(taskDef: TaskDef, testClassLoader: ClassLoader) extends Task {

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

    def report(color: String, message: String, event: Event) = {
      loggers.foreach(_.info(s"$color   $message$RESET"))
      eventHandler.handle(event)
    }


    obj.tests.foreach { case (name, fa) =>
      type AE = AssertionError // to make the lines shorter below :-\
      FTask.timed(obj.ioContextShift.shift *> fa).attempt.unsafeRunSync match {
        case Right((ms, a)) => report(GREEN, s"✓ $name ($ms ms)",          FEvent(Success, duration = ms))
        case Left(e: AE)    => report(RED,   s"✗ $name (${e.getMessage})", FEvent(Failure))
        case Left(e)        => report(RED,   s"? $name (${e.getMessage})", FEvent(Error, throwable = e))
      }
    }

    // maybe we're supposed to return new tasks with new taskdefs?
    Array.empty
  }

  def tags = Array.empty // unused

}

object FTask {
  private val now = IO(System.currentTimeMillis)
  def timed[A](fa: IO[A]): IO[(Long, A)] = (now, fa, now).mapN((t0, a, t1) => (t1 - t0, a))
}

