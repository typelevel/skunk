// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.effect.Resource
import cats.implicits._
import skunk.exception.SkunkException

object Pool {

  /** Class of exceptions raised when a resource leak is detected on pool finalization. */
  final case class ResourceLeak(expected: Int, actual: Int)
    extends SkunkException(
      sql     = None,
      message = s"A resource leak was detected during pool finalization.",
      detail  = Some(s"Expected $expected active slots, found $actual."),
      hint    = Some("""
        |The most common causes of resource leaks are (a) using a pool on a fiber that was neither
        |joined or canceled prior to pool finalization, and (b) using `Resource.allocated` and
        |failing to finalize allocated resources prior to pool finalization.
      """.stripMargin.trim.linesIterator.mkString(" "))
    )

  /**
   * A pooled resource (which is itself a managed resource).
   * @param rsrc the underlying resource to be pooled
   * @param size maximum size of the pool (must be positive)
   * @param reset a cleanup/health-check to be done before elements are returned to the pool;
   *   yielding false here means the element should be freed and removed from the pool.
   */
  def of[F[_]: Concurrent, A](
    rsrc:  Resource[F, A],
    size:  Int)(
    reset: A => F[Boolean]
  ): Resource[F, Resource[F, A]] = {

    // Just in case.
    assert(size > 0, s"Pool size must be positive (you passed $size).")

    // The type of thing allocated by rsrc.
    type Alloc = (A, F[Unit])

    // Our pool state is a pair of queues, implemented as lists because I am lazy and it's not
    // going to matter.
    type State = (
      List[Option[Alloc]],     // deque of alloc slots (filled on the left, empty on the right)
      List[Deferred[F, Alloc]] // queue of deferrals awaiting allocs
    )

    def raise[B](e: Exception): F[B] =
      Concurrent[F].raiseError(e)

    // We can construct a pool given a Ref containing our initial state.
    def poolImpl(ref: Ref[F, State]): Resource[F, A] = {

      // To give out an alloc we create a deferral first, which we will need if there are no slots
      // available. If there is a filled slot, remove it and yield its alloc. If there is an empty
      // slot, remove it and allocate (error here is raised to the user). If there are no slots,
      // enqueue the deferral and wait on it, which will [semantically] block the caller until an
      // alloc is returned to the pool.
      val give: F[Alloc] =
        Deferred[F, Alloc].flatMap { d =>
          ref.modify {
            case (Nil,           ds) => ((Nil, ds :+ d), d.get)    // enqueue … todo: should we allow a timeout here?
            case (Some(a) :: os, ds) => ((os, ds), a.pure[F])      // re-use
            case (None    :: os, ds) => ((os, ds),
              // Allocate, but if allocation fails put a new None at the end of the queue, otherwise
              // we will have leaked a slot.
              rsrc.allocated.onError { case _ =>
                ref.update { case (os, ds) => (os :+ None, ds) }
              }
            )
          } .flatten
        }

      // To take back an alloc we nominally just hand it out or push it back onto the queue, but
      // there are a bunch of error conditions to consider. This operation is a finalizer and
      // cannot be canceled, so we don't need to worry about that case here.
      def take(a: Alloc): F[Unit] =
        reset(a._1).flatMap {
          // `reset` succeeded, so hand the alloc out to the next waiting deferral if there is one,
          // otherwise return it to the head of the pool.
          case true  =>
            ref.modify {
              case (os, d :: ds) => ((os, ds), d.complete(a))          // hand it back out
              case (os, Nil)     => ((Some(a) :: os, Nil), ().pure[F]) // return to pool
            }

          // `reset` failed, so enqueue a new empty slot and finalize the alloc. If there is a
          // finalization error it will be raised to the caller.
          case false =>
            ref.modify { case (os, ds) =>  ((os :+ None, ds), a._2) }

        } .flatten.onError {

          // `reset` raised an error. Enqueue a new empty slot, finalize the alloc, and re-raise. If
          // there is an error in finalization it will trump the `reset` error.
          case t =>
            ref.modify { case (os, ds) => ((os :+ None, ds), a._2 *> t.raiseError[F, Unit]) }
               .flatten

        }

      Resource.make(give)(take).map(_._1)

    }

    // The pool itself is really just a wrapper for its state ref.
    def alloc: F[Ref[F, State]] =
      Ref[F].of((List.fill(size)(None), Nil))

    // When the pool shuts down we finalize all the allocs, which should have been returned by now.
    // Any remaining deferrals are simply abandoned. Would be nice if we could interrupt them.
    def free(ref: Ref[F, State]): F[Unit] =
      ref.get.flatMap {

        // We could check here to ensure that os.length = size and log an error if an entry is
        // missing. This would indicate a leak. If there is an error in `free` it will halt
        // finalization of remaining allocs and will be re-raised to the caller, which is a bit
        // harsh. We might want to accumulate failures and raise one big error when we're done.
        case (os, _) =>
          raise(ResourceLeak(size, os.length)).whenA(os.length != size) *>
          os.traverse_ {
            case Some((_, free)) => free
            case None            => ().pure[F]
          }

      }

    Resource.make(alloc)(free).map(poolImpl)

  }

}