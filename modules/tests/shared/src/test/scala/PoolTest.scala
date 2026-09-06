// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import ffstest.FTest
import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all._
import scala.concurrent.duration._
import skunk.util.Pool
import cats.effect.Ref
import skunk.util.Pool.ResourceLeak
import cats.effect.Deferred
import scala.util.Random
import skunk.util.Pool.ShutdownException
import org.typelevel.otel4s.trace.Tracer
import skunk.util.Recycler
import cats.effect.testkit.TestControl
import skunk.telemetry.Telemetry

class PoolTest extends FTest {

  implicit def telemetry(implicit tracer: Tracer[IO]): Telemetry[IO] =
    skunk.TestTelemetry("pool-test")

  case class UserFailure() extends Exception("user failure")
  case class AllocFailure() extends Exception("allocation failure")
  case class FreeFailure() extends Exception("free failure")
  case class ResetFailure() extends Exception("reset failure")

  val ints: IO[Resource[IO, Int]] =
    Ref[IO].of(1).map { ref =>
      val next = ref.modify(n => (n + 1, n.pure[IO])).flatten
      Resource.make(next)(_ => IO.unit)
    }

  // list of computations into computation that yields results one by one
  def yielding[A](fas: IO[A]*): IO[IO[A]] =
    Ref[IO].of(fas.toList).map { ref =>
      ref.modify {
        case Nil       => (Nil, IO.raiseError(new Exception("No more values!")))
        case fa :: fas => (fas, fa)
      } .flatten
    }

  def resourceYielding[A](fas: IO[A]*): IO[Resource[IO, A]] =
    yielding(fas: _*).map(Resource.make(_)(_ => IO.unit))

  // This test leaks
  tracedTestWithTracer("error in alloc is rethrown to caller (immediate)") { implicit tracer: Tracer[IO] =>
    val rsrc = Resource.make(IO.raiseError[String](AllocFailure()))(_ => IO.unit)
    val pool = Pool.ofF({(_: Telemetry[IO]) => rsrc}, 42)(Recycler.success)
    pool.use(_(Telemetry[IO]).use(_ => IO.unit)).assertFailsWith[AllocFailure]
  }

  tracedTestWithTracer("error in alloc is rethrown to caller (deferral completion following errored cleanup)") { implicit tracer: Tracer[IO] =>
    resourceYielding(IO(1), IO.raiseError(AllocFailure())).flatMap { r =>
      val p = Pool.ofF({(_: Telemetry[IO]) => r}, 1)(Recycler[IO, Int](_ => IO.raiseError(ResetFailure())))
      p.use { r =>
        for {
          d  <- Deferred[IO, Unit]
          f1 <- r(Telemetry[IO]).use(n => assertEqual("n should be 1", n, 1) *> d.get).assertFailsWith[ResetFailure].start
          f2 <- r(Telemetry[IO]).use(_ => fail[Int]("should never get here")).assertFailsWith[AllocFailure].start
          _  <- d.complete(())
          _  <- f1.join
          _  <- f2.join
        } yield ()
      }
    }
  }

  tracedTestWithTracer("error in alloc is rethrown to caller (deferral completion following failed cleanup)") { implicit tracer: Tracer[IO] =>
    resourceYielding(IO(1), IO.raiseError(AllocFailure())).flatMap { r =>
      val p = Pool.ofF({(_: Telemetry[IO]) => r}, 1)(Recycler.failure)
      p.use { r =>
        for {
          d  <- Deferred[IO, Unit]
          f1 <- r(Telemetry[IO]).use(n => assertEqual("n should be 1", n, 1) *> d.get).start
          f2 <- r(Telemetry[IO]).use(_ => fail[Int]("should never get here")).assertFailsWith[AllocFailure].start
          _  <- d.complete(())
          _  <- f1.join
          _  <- f2.join
        } yield ()
      }
    }
  }

  tracedTestWithTracer("error in finalizer does not prevent cleanup of deferreds") { implicit tracer: Tracer[IO] =>
    val r = Resource.make(IO(1))(_ => IO.raiseError(ResetFailure()))
    val p = Pool.ofF({(_: Telemetry[IO]) => r}, 1)(Recycler.failure)
    p.use { r =>
      val tx = r(Telemetry[IO]).use(_ => IO.unit)
      List(tx, tx).parSequence
    }.assertFailsWith[ResetFailure]
  }

  tracedTestWithTracer("provoke dangling deferral cancellation") { implicit tracer: Tracer[IO] =>
    ints.flatMap { r =>
      val p = Pool.ofF({(_: Telemetry[IO]) => r}, 1)(Recycler.failure)
      Deferred[IO, Either[Throwable, Int]].flatMap { d1 =>
        p.use { r =>
          for {
            d <- Deferred[IO, Unit]
            _ <- r(Telemetry[IO]).use(_ => d.complete(()) *> IO.never).start // leaked forever
            _ <- d.get // make sure the resource has been allocated
            f <- r(Telemetry[IO]).use(_ => fail[Int]("should never get here")).attempt.flatMap(d1.complete).start // defer
            _ <- IO.sleep(100.milli) // ensure that the fiber has a chance to run
          } yield f
        } .assertFailsWith[ResourceLeak].flatMap {
          case ResourceLeak(1, 0, 1) => d1.get.flatMap(_.liftTo[IO])
          case e                     => e.raiseError[IO, Unit]
        } .assertFailsWith[ShutdownException.type].void
    }
  }}

  tracedTestWithTracer("error in free is rethrown to caller") { implicit tracer: Tracer[IO] =>
    val rsrc = Resource.make("foo".pure[IO])(_ => IO.raiseError(FreeFailure()))
    val pool = Pool.ofF({(_: Telemetry[IO]) => rsrc}, 42)(Recycler.success)
    pool.use(_(Telemetry[IO]).use(_ => IO.unit)).assertFailsWith[FreeFailure]
  }

  tracedTestWithTracer("error in reset is rethrown to caller") { implicit tracer: Tracer[IO] =>
    val rsrc = Resource.make("foo".pure[IO])(_ => IO.unit)
    val pool = Pool.ofF({(_: Telemetry[IO]) => rsrc}, 42)(Recycler[IO, String](_ => IO.raiseError(ResetFailure())))
    pool.use(_(Telemetry[IO]).use(_ => IO.unit)).assertFailsWith[ResetFailure]
  }

  tracedTestWithTracer("reuse on serial access") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, 3)(Recycler.success)).flatMap { factory =>
      factory.use { pool =>
        pool(Telemetry[IO]).use { n =>
          assertEqual("first num should be 1", n, 1)
        } *>
        pool(Telemetry[IO]).use { n =>
          assertEqual("we should get it again", n, 1)
        }
      }
    }
  }

  tracedTestWithTracer("allocation on nested access") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, 3)(Recycler.success)).flatMap { factory =>
      factory.use { pool =>
        pool(Telemetry[IO]).use { n =>
          assertEqual("first num should be 1", n, 1) *>
          pool(Telemetry[IO]).use { n =>
            assertEqual("but this one should be 2", n, 2)
          } *>
          pool(Telemetry[IO]).use { n =>
            assertEqual("and again", n, 2)
          }
        }
      }
    }
  }

  tracedTestWithTracer("allocated resource can cause a leak, which will be detected on finalization") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, 3)(Recycler.success)).flatMap { factory =>
      factory.use { pool =>
        pool(Telemetry[IO]).allocated
      } .assertFailsWith[ResourceLeak].flatMap {
        case ResourceLeak(expected, actual, _) =>
          assert("expected 1 leakage", expected - actual == 1)
      }
    }
  }

  tracedTestWithTracer("unmoored fiber can cause a leak, which will be detected on finalization") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, 3)(Recycler.success)).flatMap { factory =>
      factory.use { pool =>
        pool(Telemetry[IO]).use(_ => IO.never).start *>
        IO.sleep(100.milli) // ensure that the fiber has a chance to run
      } .assertFailsWith[ResourceLeak].flatMap {
        case ResourceLeak(expected, actual, _) =>
          assert("expected 1 leakage", expected - actual == 1)
      }
    }
  }

  // Concurrency tests below. These are nondeterministic and need a lot of exercise.

  val PoolSize = 10
  val ConcurrentTasks = 500

  val shortRandomDelay = IO((Random.nextInt() % 100).abs.milliseconds)

  tracedTestWithTracer("progress and safety with many fibers") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, PoolSize)(Recycler.success)).flatMap { factory =>
      (1 to ConcurrentTasks).toList.parTraverse_{ _ =>
        factory.use { p =>
          p(Telemetry[IO]).use { _ =>
            for {
              t <- shortRandomDelay
              _ <- IO.sleep(t)
            } yield ()
          }
        }
      }
    }
  }

  tracedTestWithTracer("progress and safety with many fibers and cancellation") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, PoolSize)(Recycler.success)).flatMap { factory =>
      factory.use { pool =>
        (1 to ConcurrentTasks).toList.parTraverse_{_ =>
          for {
            t <- shortRandomDelay
            f <- pool(Telemetry[IO]).use(_ => IO.sleep(t)).start
            _ <- if (t > 50.milliseconds) f.join else f.cancel
          } yield ()
        }
      }
    }
  }

  tracedTestWithTracer("progress and safety with many fibers and user failures") { implicit tracer: Tracer[IO] =>
    ints.map(a => Pool.ofF({(_: Telemetry[IO]) => a}, PoolSize)(Recycler.success)).flatMap { factory =>
      factory.use { pool =>
        (1 to ConcurrentTasks).toList.parTraverse_{ _ =>
          pool(Telemetry[IO]).use { _ =>
            for {
              t <- shortRandomDelay
              _ <- IO.sleep(t)
              _ <- IO.raiseError(UserFailure()).whenA(t < 50.milliseconds)
            } yield ()
          } .attempt // swallow errors so we don't fail fast
        }
      }
    }
  }

  tracedTestWithTracer("progress and safety with many fibers and allocation failures") { implicit tracer: Tracer[IO] =>
    val alloc = IO(Random.nextBoolean()).flatMap {
      case true  => IO.unit
      case false => IO.raiseError(AllocFailure())
    }
    val rsrc = Resource.make(alloc)(_ => IO.unit)
    Pool.ofF({(_: Telemetry[IO]) => rsrc}, PoolSize)(Recycler.success).use { pool =>
      (1 to ConcurrentTasks).toList.parTraverse_{ _ =>
        pool(Telemetry[IO]).use { _ =>
          IO.unit
        } .attempt
      }
    }
  }

  tracedTestWithTracer("progress and safety with many fibers and freeing failures") { implicit tracer: Tracer[IO] =>
    val free = IO(Random.nextBoolean()).flatMap {
      case true  => IO.unit
      case false => IO.raiseError(FreeFailure())
    }
    val rsrc  = Resource.make(IO.unit)(_ => free)
    Pool.ofF({(_: Telemetry[IO]) => rsrc}, PoolSize)(Recycler.success).use { pool =>
      (1 to ConcurrentTasks).toList.parTraverse_{ _ =>
        pool(Telemetry[IO]).use { _ =>
          IO.unit
        } .attempt
      }
    } .handleErrorWith {
      // cleanup here may raise an exception, so we need to handle that
      case FreeFailure() => IO.unit
      case t => throw t
    }
  }

  tracedTestWithTracer("progress and safety with many fibers and reset failures") { implicit tracer: Tracer[IO] =>
    val recycle = IO(Random.nextInt(3)).flatMap {
      case 0 => true.pure[IO]
      case 1 => false.pure[IO]
      case 2 => IO.raiseError(ResetFailure())
    }
    val rsrc  = Resource.make(IO.unit)(_ => IO.unit)
    Pool.ofF({(_: Telemetry[IO]) => rsrc}, PoolSize)(Recycler(_ => recycle)).use { pool =>
      (1 to ConcurrentTasks).toList.parTraverse_{ _ =>
        pool(Telemetry[IO]).use { _ =>
          IO.unit
        } handleErrorWith {
          case ResetFailure() => IO.unit
          case t => throw t
        }
      }
    }
  }

  tracedTestWithTracer("unhealthy pooled resource is discarded and replaced on checkout") { implicit tracer: Tracer[IO] =>
    for {
      healthy <- Ref[IO].of(true)
      freed   <- Ref[IO].of(List.empty[Int])
      counter <- Ref[IO].of(1)
      rsrc     = Resource.make(counter.getAndUpdate(_ + 1))(n => freed.update(_ :+ n))
      _       <- Pool.ofF({(_: Telemetry[IO]) => rsrc}, 1, Recycler[IO, Int](_ => healthy.get), Recycler.success[IO, Int]).use { pool =>
                   for {
                     _ <- pool(Telemetry[IO]).use(n => assertEqual("first checkout", n, 1))
                     _ <- healthy.set(false)
                     _ <- pool(Telemetry[IO]).use(n => assertEqual("second checkout", n, 2))
                     _ <- freed.get.flatMap(assertEqual("the dead resource was freed", _, List(1)))
                   } yield ()
                 }
    } yield ()
  }

  tracedTestWithTracer("health check that raises discards the resource rather than failing the checkout") { implicit tracer: Tracer[IO] =>
    for {
      first   <- Ref[IO].of(true)
      counter <- Ref[IO].of(1)
      rsrc     = Resource.make(counter.getAndUpdate(_ + 1))(_ => IO.unit)
      check    = Recycler[IO, Int](_ => first.getAndSet(false).ifM(IO.raiseError[Boolean](AllocFailure()), true.pure[IO]))
      _       <- Pool.ofF({(_: Telemetry[IO]) => rsrc}, 1, check, Recycler.success[IO, Int]).use { pool =>
                   pool(Telemetry[IO]).use_ *>
                   pool(Telemetry[IO]).use(n => assertEqual("second checkout", n, 2))
                 }
    } yield ()
  }

  test("cancel while waiting") { implicit tracer: Tracer[IO] =>
    TestControl.executeEmbed {
      Pool.of(Resource.unit[IO], 1)(Recycler.success).use { pool =>
        pool.useForever.background.surround { // take away the resource ...
          // ... to force this one to wait
          pool.use_.timeoutTo(1.millis, IO.unit) // it should not hang on cancelation
        }
      } // we should also not get a ResourceLeak error
    }
  }

}
