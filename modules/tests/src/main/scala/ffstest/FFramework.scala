package ffstest

import cats.effect._
import cats.implicits._
import sbt.testing.{ Framework, _ }
import scala.concurrent.ExecutionContext

trait FTest {

  protected[ffstest] var tests =
    List.empty[(String, IO[_])]

  implicit val ioContextShift: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  implicit val ioTimer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  def test[A](name: String)(f: IO[A]) =
    tests = tests :+ ((name, f))

  def assert(msg: => String, b: => Boolean): IO[Unit] =
    if (b) IO.pure(())
    else IO.raiseError(new AssertionError(msg))

}

class FFramework extends Framework {
  val name = "ffstest"
  val fingerprints = Array(FFramework.fingerprint)
  def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
    new FRunner(args, remoteArgs, testClassLoader)
}

object FFramework {
  val fingerprint: Fingerprint =
    new SubclassFingerprint {
      val isModule = true
      def requireNoArgConstructor = true
      def superclassName = classOf[FTest].getName
    }
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
  def tags = Array.empty
  def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {

    loggers.foreach(_.info(Console.YELLOW + "🍋  " + taskDef.fullyQualifiedName + Console.RESET))

    val cls = Class.forName(taskDef.fullyQualifiedName + "$", true, testClassLoader)
    val obj = cls.getField("MODULE$").get(null).asInstanceOf[FTest]

    obj.tests.foreach { case (name, fa) =>
      FTask.timed(obj.ioContextShift.shift *> fa).attempt.unsafeRunSync match {

        case Left(e: AssertionError) =>
          loggers.foreach(_.info(s"${Console.RED}   ✗ $name (${e.getMessage})${Console.RESET}"))
          eventHandler.handle(new Event {
            val duration: Long = -1L
            val fingerprint: Fingerprint = taskDef.fingerprint
            val fullyQualifiedName: String = taskDef.fullyQualifiedName
            val selector: Selector =  taskDef.selectors.head // doesn't seem to do anything
            val status: Status = Status.Failure
            val throwable: OptionalThrowable = new OptionalThrowable()
          })

        case Left(e) =>
          loggers.foreach(_.info(s"${Console.RED}   ? $name (${e.getMessage})${Console.RESET}"))
          eventHandler.handle(new Event {
            val duration: Long = -1L
            val fingerprint: Fingerprint = taskDef.fingerprint
            val fullyQualifiedName: String = taskDef.fullyQualifiedName
            val selector: Selector = taskDef.selectors.head // doesn't seem to do anything
            val status: Status = Status.Error
            val throwable: OptionalThrowable = new OptionalThrowable(e)
          })

        case Right((ms, a)) =>
          loggers.foreach(_.info(s"${Console.GREEN}   ✓ $name ($ms ms)${Console.RESET}"))
          eventHandler.handle(new Event {
            val duration: Long = ms
            val fingerprint: Fingerprint = taskDef.fingerprint
            val fullyQualifiedName: String = taskDef.fullyQualifiedName
            val selector: Selector = taskDef.selectors.head // doesn't seem to do anything
            val status: Status = Status.Success
            val throwable: OptionalThrowable = new OptionalThrowable()
          })

      }

    }

    // maybe we're supposed to return new tasks with new taskdefs?
    Array.empty
  }

}

object FTask {
  val now = IO(System.currentTimeMillis)
  def timed[A](fa: IO[A]): IO[(Long, A)] =
    for {
      t0 <- now
      a  <- fa
      t1 <- now
    } yield (t1 - t0, a)
}

