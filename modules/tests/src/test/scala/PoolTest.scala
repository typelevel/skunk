package tests

import ffstest.FTest
import cats.effect.IO
import cats.effect.Resource
import cats.implicits._
import scala.concurrent.duration._
import skunk.util.Pool
import cats.effect.concurrent.Ref
import skunk.util.Pool.ResourceLeak

case object PoolTest extends FTest {

  case class IntentionalFailure() extends Exception("intentional")

  val ints: IO[Resource[IO, Int]] =
    Ref[IO].of(1).map { ref =>
      val next = ref.modify(n => (n + 1, n.pure[IO])).flatten
      Resource.make(next)(_ => IO.unit)
    }

  // This test leaks
  test("error in alloc is rethrown to caller") {
    val rsrc = Resource.make(IO.raiseError[String](IntentionalFailure()))(_ => IO.unit)
    val pool = Pool.of(rsrc, 42)(_ => true.pure[IO])
    pool.use(_.use(_ => IO.unit)).assertFailsWith[IntentionalFailure]
  }

  test("error in free is rethrown to caller") {
    val rsrc = Resource.make("foo".pure[IO])(_ => IO.raiseError(IntentionalFailure()))
    val pool = Pool.of(rsrc, 42)(_ => true.pure[IO])
    pool.use(_.use(_ => IO.unit)).assertFailsWith[IntentionalFailure]
  }

  test("error in reset is rethrown to caller") {
    val rsrc = Resource.make("foo".pure[IO])(_ => IO.unit)
    val pool = Pool.of(rsrc, 42)(_ => IO.raiseError(IntentionalFailure()))
    pool.use(_.use(_ => IO.unit)).assertFailsWith[IntentionalFailure]
  }

  test("reuse on serial access") {
    ints.map(Pool.of(_, 3)(_ => true.pure[IO])).flatMap { factory =>
      factory.use { pool =>
        pool.use { n =>
          assertEqual("first num should be 1", n, 1)
        } *>
        pool.use { n =>
          assertEqual("we should get it again", n, 1)
        }
      }
    }
  }

  test("allocation on nested access") {
    ints.map(Pool.of(_, 3)(_ => true.pure[IO])).flatMap { factory =>
      factory.use { pool =>
        pool.use { n =>
          assertEqual("first num should be 1", n, 1) *>
          pool.use { n =>
            assertEqual("but this one should be 2", n, 2)
          } *>
          pool.use { n =>
            assertEqual("and again", n, 2)
          }
        }
      }
    }
  }

  test("allocated resource can cause a leak, which will be detected on finalization") {
    ints.map(Pool.of(_, 3)(_ => true.pure[IO])).flatMap { factory =>
      factory.use { pool =>
        pool.allocated
      } .assertFailsWith[ResourceLeak].flatMap {
        case ResourceLeak(expected, actual) =>
          assert("expected 1 leakage", expected - actual == 1)
      }
    }
  }

  test("unmoored fiber can cause a leak, which will be detected on finalization") {
    ints.map(Pool.of(_, 3)(_ => true.pure[IO])).flatMap { factory =>
      factory.use { pool =>
        pool.use(_ => IO.never).start
      } .assertFailsWith[ResourceLeak].flatMap {
        case ResourceLeak(expected, actual) =>
          assert("expected 1 leakage", expected - actual == 1)
      }
    }
  }

  test("progress and safety with many fibers") {
    ints.map(Pool.of(_, 10)(_ => true.pure[IO])).flatMap { factory =>
      (1 to 100).toList.parTraverse_{ _ =>
        factory.use { p =>
          p.use { _ =>
            for {
              t <- IO(util.Random.nextInt % 100)
              _ <- IO.sleep(t.milliseconds)
            } yield ()
          }
        }
      }
    }
  }

  test("progress and safety with many fibers and cancellation") {
    ints.map(Pool.of(_, 10)(_ => true.pure[IO])).flatMap { factory =>
      factory.use { pool =>
        (1 to 100).toList.parTraverse_{_ =>
          for {
            t <- IO(util.Random.nextInt % 100)
            f <- pool.use(_ => IO.sleep(t.milliseconds)).start
            _ <- if (t > 50) f.join else f.cancel
          } yield ()
        }
      }
    }
  }

  test("progress and safety with many fibers and user failures") {
    ints.map(Pool.of(_, 10)(_ => true.pure[IO])).flatMap { factory =>
      factory.use { pool =>
        (1 to 100).toList.parTraverse_{ _ =>
          pool.use { _ =>
            for {
              t <- IO(util.Random.nextInt % 100)
              _ <- IO.sleep(t.milliseconds)
              _ <- IO.raiseError(IntentionalFailure()).whenA(t < 50)
            } yield ()
          } .attempt // swallow errors so we don't fail fast
        }
      }
    }
  }

  test("progress and safety with many fibers and allocation failures") {
    val alloc = IO(util.Random.nextBoolean).flatMap {
      case true  => IO.unit
      case false => IO.raiseError(IntentionalFailure())
    }
    val rsrc = Resource.make(alloc)(_ => IO.unit)
    Pool.of(rsrc, 10)(_ => true.pure[IO]).use { pool =>
      (1 to 100).toList.parTraverse_{ _ =>
        pool.use { _ =>
          IO.unit
        } .attempt
      }
    }
  }

  test("progress and safety with many fibers and freeing failures") {
    val free = IO(util.Random.nextBoolean).flatMap {
      case true  => IO.unit
      case false => IO.raiseError(IntentionalFailure())
    }
    val rsrc  = Resource.make(IO.unit)(_ => free)
    Pool.of(rsrc, 10)(_ => true.pure[IO]).use { pool =>
      (1 to 100).toList.parTraverse_{ _ =>
        pool.use { _ =>
          IO.unit
        } .attempt
      }
    } .handleErrorWith {
      // cleanup here may raise an exception, so we need to handle that
      case IntentionalFailure() => IO.unit
    }
  }

  test("progress and safety with many fibers and reset failures") {
    val reset = IO(util.Random.nextInt(3)).flatMap {
      case 0  => true.pure[IO]
      case 1 => false.pure[IO]
      case 2 => IO.raiseError(IntentionalFailure())
    }
    val rsrc  = Resource.make(IO.unit)(_ => IO.unit)
    Pool.of(rsrc, 10)(_ => reset).use { pool =>
      (1 to 100).toList.parTraverse_{ _ =>
        pool.use { _ =>
          IO.unit
        } .attempt
      }
    } .handleErrorWith {
      // cleanup here may raise an exception, so we need to handle that
      case IntentionalFailure() => IO.unit
    }
  }

}