// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.effect._
import cats.effect.concurrent._
import cats.implicits._


object Pool {

  /**
   * Resource that yields a non-blocking **pooled** version of `rsrc`, with up to `maxInstances`
   * live instances permitted, constructed lazily. A released instance is returned to the pool if
   * `reset` yields `true` (the typical case), otherwise it is discarded. This gives the pool a
   * chance to verify that an instance is valid, and reset it to a "clean" state if necessary. All
   * instances are released when the pool is released, in an undefined order (i.e., don't use this
   * if `rsrc` yields instances that depend on each other).
   */
  def of[F[_]: Concurrent, A](
    rsrc: Resource[F, A],
    maxInstances: Long,
    reset: A => F[Boolean]
  ): Pool[F, A] =
    Resource.make(PoolData.create(rsrc, maxInstances, reset))(_.close).map(_.resource)

  /**
   * Internal state used by a pool. We need an underlying resource, a counting semaphore to limit
   * concurrent instances, a cache to store instances for reuse, and a reset program constructor
   * for checking validity (and arbitrary cleanup) of returned instances.
   */
  private final class PoolData[F[_]: Concurrent, A](
    underlying: Resource[F, A],
    semaphore:  Semaphore[F],
    cache:      MVar[F, List[Leak[F, A]]],
    reset:      A => F[Boolean]
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
    private def release(leak: Leak[F, A]): F[Unit] =
      cache.take.flatMap { q =>
        reset(leak.value).attempt.flatMap {
          case Right(true)  => cache.put(leak :: q) *> semaphore.release
          case Right(false) => cache.put(q)         *> semaphore.release
          case Left(e)      => cache.put(q)         *> semaphore.release *> Concurrent[F].raiseError(e)
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
