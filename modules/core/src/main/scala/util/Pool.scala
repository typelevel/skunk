package skunk.util

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._

object Pool {

  def of[F[_]: Concurrent, A](
    rsrc: Resource[F, A],
    maxInstances: Long,
    reset: A => F[Boolean]
  ): Pool[F, A] =
    Resource.make(PoolData.create(rsrc, maxInstances, reset))(_.close).map(_.resource)

  /**
   * Internal state used by a pool. We need an underlying resource, a counting semaphore to limit
   * concurrent instances, and a cache to store instances for reuse. This thing itself needs to be
   * controlled by a Resource because it must be closed to free up temporarily leaked resources.
   */
  private final class PoolData[F[_]: Concurrent, A](
    underlying: Resource[F, A],
    semaphore:  Semaphore[F],
    cache:      MVar[F, List[Leak[F, A]]],
    reset:    A => F[Boolean]
  ) {

    // Take a permit and yield a leaked resource from the queue, or leak a new one if necessary
    // If an error is raised leaking the resource we give up the permit and re-throw
    private def take(factory: F[Leak[F, A]]): F[Leak[F, A]] =
      for {
        _   <- semaphore.acquire
        q   <- cache.take
        lfa <-
          q match {
            case a :: as => cache.put(as).as(a)
            case Nil     => cache.put(Nil) *> factory.onError { case _ => semaphore.release }
          }
      } yield lfa

    // Add a leaked resource to the pool and release a permit
    // TODO: some kind of health check A => F[Boolean] on release
    private def release(leak: Leak[F, A]): F[Unit] =
      cache.take.flatMap { q =>
        reset(leak.value).attempt.flatMap {
          case Right(true) => cache.put(leak :: q) *> semaphore.release
          case Right(left) => cache.put(q) *> semaphore.release
          case Left(e) => cache.put(q) *> semaphore.release *> Concurrent[F].raiseError(e)
        }
      }

    // Release all resources
    def close: F[Unit] =
      for {
        leaks <- cache.take
        _     <- leaks.traverse(_.release.attempt) // on error no big deal?
      } yield ()

    // View this bundle of nastiness as a resource
    def resource: Resource[F, A] =
      Resource.make(take(Leak.of(underlying)))(release).map(_.value)

  }

  private object PoolData {

    def create[F[_]: Concurrent, A](
      rsrc: Resource[F, A],
      maxInstances: Long,
      reset: A => F[Boolean]
    ): F[PoolData[F, A]] =
      for {
        sem   <- Semaphore[F](maxInstances)
        cache <- MVar[F].of(List.empty[Leak[F, A]])
      } yield new PoolData(rsrc, sem, cache, reset)

  }

}






object PoolExample extends IOApp {

  def rsrc: IO[Resource[IO, Int]] =
    MVar[IO].of(1).map { mv =>
      val alloc: IO[Int] =
        for {
          n <- mv.take
          _ <- printAny(s"alloc $n")
          _ <- IO.raiseError(new RuntimeException(s"failing at $n")).whenA(n > 4)
          _ <- mv.put(n + 1)
        } yield n
      Resource.make(alloc)(a => printAny(s"free $a"))
    }

  def printAny(a: Any): IO[Unit] =
    IO(println(s"${Thread.currentThread.getId} - $a")) *> IO.shift

  def run(args: List[String]): IO[ExitCode] =
    rsrc.flatMap { r =>
      Pool.of(r, 5, (n: Int) => IO(println(s"resetting $n")).as(true)).use { p =>
        for {
          // _ <- p.use(printAny)
          // _ <- p.use(printAny)
          // _ <- p.use(printAny)
          f <- List.fill(10)(p.use(printAny)).parSequence.start
          _ <- p.use(a => printAny(a) *> p.use(printAny))
          _ <- f.join
        } yield ExitCode.Success
      }
    }

}