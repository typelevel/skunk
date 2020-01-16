// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.effect.Concurrent
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.Ref
import cats.effect.implicits._
import cats.effect.Resource
import cats.implicits._
import skunk.exception.SkunkException
import natchez.Trace

object Pool {

  /** Class of exceptions raised when a resource leak is detected on pool finalization. */
  final case class ResourceLeak(expected: Int, actual: Int, deferrals: Int)
    extends SkunkException(
      sql     = None,
      message = s"A resource leak was detected during pool finalization.",
      detail  = Some(s"Expected $expected active slot(s) and no deferrals, found $actual slots and $deferrals deferral(s)."),
      hint    = Some("""
        |The most common causes of resource leaks are (a) using a pool on a fiber that was neither
        |joined or canceled prior to pool finalization, and (b) using `Resource.allocated` and
        |failing to finalize allocated resources prior to pool finalization.
      """.stripMargin.trim.linesIterator.mkString(" "))
    )

  /**
   * Exception raised to deferrals that remain during pool finalization. This indicates a
   * programming error, typically misuse of fibers.
   */
  object ShutdownException
    extends SkunkException(
      sql     = None,
      message = "The pool is being finalized and no more resources are available.",
      hint    = Some("""
        |The most common cause of this exception is using a pool on a fiber that was neither
        |joined or canceled prior to pool finalization.
      """.stripMargin.trim.linesIterator.mkString(" "))
    )

  /**
   * A pooled resource (which is itself a managed resource).
   * @param rsrc the underlying resource to be pooled
   * @param size maximum size of the pool (must be positive)
   * @param recycler a cleanup/health-check to be done before elements are returned to the pool;
   *   yielding false here means the element should be freed and removed from the pool.
   */
  def of[F[_]: Concurrent: Trace, A](
    rsrc:  Resource[F, A],
    size:  Int)(
    recycler: Recycler[F, A]
  ): Resource[F, Resource[F, A]] = {

    // Just in case.
    assert(size > 0, s"Pool size must be positive (you passed $size).")

    // The type of thing allocated by rsrc.
    type Alloc = (A, F[Unit])

    // Our pool state is a pair of queues, implemented as lists because I am lazy and it's not
    // going to matter.
    type State = (
      List[Option[Alloc]],     // deque of alloc slots (filled on the left, empty on the right)
      List[Deferred[F, Either[Throwable, Alloc]]] // queue of deferrals awaiting allocs
    )

    // We can construct a pool given a Ref containing our initial state.
    def poolImpl(ref: Ref[F, State]): Resource[F, A] = {

      // To give out an alloc we create a deferral first, which we will need if there are no slots
      // available. If there is a filled slot, remove it and yield its alloc. If there is an empty
      // slot, remove it and allocate. If there are no slots, enqueue the deferral and wait on it,
      // which will [semantically] block the caller until an alloc is returned to the pool.
      val give: F[Alloc] =
        Trace[F].span("pool.allocate") {
          Deferred[F, Either[Throwable, Alloc]].flatMap { d =>

            // If allocation fails for any reason then there's no resource to return to the pool
            // later, so in this case we have to append a new empty slot to the queue. We do this in
            // a couple places here so we factored it out.
            val restore: PartialFunction[Throwable, F[Unit]] = {
              case _ => ref.update { case (os, ds) => (os :+ None, ds) }
            }

            // Here we go. The cases are a full slot (done), an empty slot (alloc), and no slots at
            // all (defer and wait).
            ref.modify {
              case (Some(a) :: os, ds) => ((os, ds), a.pure[F])
              case (None    :: os, ds) => ((os, ds), rsrc.allocated.onError(restore))
              case (Nil,           ds) => ((Nil, ds :+ d), d.get.flatMap(_.liftTo[F].onError(restore)))
            } .flatten

          }
        }

      // To take back an alloc we nominally just hand it out or push it back onto the queue, but
      // there are a bunch of error conditions to consider. This operation is a finalizer and
      // cannot be canceled, so we don't need to worry about that case here.
      def take(a: Alloc): F[Unit] =
        Trace[F].span("pool.free") {
          recycler(a._1).onError {
            case t     => dispose(a) *> t.raiseError[F, Unit]
          } flatMap {
            case true  => recycle(a)
            case false => dispose(a)
          }
        }

      // Return `a` to the pool. If there are awaiting deferrals, complete the next one. Otherwise
      // push a filled slot into the queue.
      def recycle(a: Alloc): F[Unit] =
        Trace[F].span("recycle") {
          ref.modify {
            case (os, d :: ds) => ((os, ds), d.complete(a.asRight))  // hand it back out
            case (os, Nil)     => ((Some(a) :: os, Nil), ().pure[F]) // return to pool
          } .flatten
        }

      // Something went wrong when returning `a` to the pool so let's dispose of it and figure out
      // how to clean things up. If there are no deferrals, append an empty slot to take the place
      // of `a`. If there are deferrals, remove the next one and complete it (failures in allocation
      // are handled by the awaiting deferral in `give` above). Always finalize `a`
      def dispose(a: Alloc): F[Unit] =
        Trace[F].span("dispose") {
          ref.modify {
            case (os, Nil) =>  ((os :+ None, Nil), ().pure[F]) // new empty slot
            case (os, d :: ds) =>  ((os, ds), rsrc.allocated.attempt.flatMap(d.complete)) // alloc now!
          } .guarantee(a._2).flatten
        }

      // Hey, that's all we need to create our resource!
      Resource.make(give)(take).map(_._1)

    }

    // The pool itself is really just a wrapper for its state ref.
    def alloc: F[Ref[F, State]] =
      Ref[F].of((List.fill(size)(None), Nil))

    // When the pool shuts down we finalize all the allocs, which should have been returned by now.
    // Any remaining deferrals (there should be none, but can be due to poor fiber hygeine) are
    // completed with `ShutdownException`.
    def free(ref: Ref[F, State]): F[Unit] =
      ref.get.flatMap {

        // Complete all awaiting deferrals with a `ShutdownException`, then raise an error if there
        // are fewer slots than the pool size. Both conditions can be provoked by poor resource
        // hygiene (via fibers typically). Then finalize any remaining pooled elements. Failure of
        // pool finalization may result in unfinalized resources. To be improved.
        case (os, ds) =>
          ds.traverse(_.complete(Left(ShutdownException))) *>
          ResourceLeak(size, os.length, ds.length).raiseError[F, Unit].whenA(os.length != size) *>
          os.traverse_ {
            case Some((_, free)) => free
            case None            => ().pure[F]
          }

      }

    Resource.make(alloc)(free).map(poolImpl)

  }

}