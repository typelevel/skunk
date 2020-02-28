// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats._
import cats.implicits._

/**
 * Encapsulates a function that consumes a value and produces a computation that peforms some
 * operation and yields `true` on success and `false` on failure. Intended use is with resource
 * pools, where you may wish to do a health check or reset some state when a value is handed back
 * in (or yield `false`, indicating that the value should be discarded). The only point to this
 * encapsulation is that it allows us to define a monoid!
 */
final case class Recycler[F[_], A](run: A => F[Boolean]) extends (A => F[Boolean]) {

  def apply(a: A): F[Boolean] =
    run(a)

  def contramap[B](f: B => A): Recycler[F, B] =
    Recycler(b => run(f(b)))

  def mapK[G[_]](fk: F ~> G): Recycler[G, A] =
    Recycler(run.map(fk(_)))

  /**
   * Compose this `Recycler` sequentially with `other`. The returned `Recycler` will short-circuit
   * and yield `false` immediately on failure, without running `other`. This allows recyclers to
   * fail quickly.
   */
  def andAlso(other: Recycler[F, A])(
    implicit ev: Monad[F]
  ): Recycler[F, A] =
    Recycler { a =>
      run(a).flatMap {
        case true  => other(a)
        case false => false.pure[F]
      }
    }

}


object Recycler {

  /** Recycler forms a monoid with "andAlso" logic, if `F` is a monad. */
  implicit def monoidRecycle[F[_]: Monad]: MonoidK[Recycler[F, ?]] =
    new MonoidK[Recycler[F, ?]] {
      def empty[A] = success[F, A]
      def combineK[A](x: Recycler[F,A], y: Recycler[F,A]): Recycler[F,A] = x andAlso y
    }

  /** Recycler is a contravariant functor. */
  implicit def contravariantRecycle[F[_]]: Contravariant[Recycler[F, ?]] =
    new Contravariant[Recycler[F, ?]] {
      def contramap[A, B](fa: Recycler[F,A])(f: B => A) = fa.contramap(f)
    }

  /** Recycler that always yields `true`. */
  def success[F[_]: Applicative, A]: Recycler[F, A] =
    Recycler(_ => true.pure[F])

  /** Recycler that always yields `false`. */
  def failure[F[_]: Applicative, A]: Recycler[F, A] =
    Recycler(_ => false.pure[F])

}
