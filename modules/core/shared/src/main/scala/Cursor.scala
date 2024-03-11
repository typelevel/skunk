// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.{ Functor, ~> }
import cats.syntax.all._

/**
 * An open cursor from which rows can be fetched, valid during the lifetime its defining `Session`.
 * You can use this mechanism to implement chunked reads and paged results, although it is ofen
 * more pleasant to use a `Cursor`-backed `Stream`, as produced by [[PreparedQuery#stream]].
 * @group Session
 */
trait Cursor[F[_], A] { outer =>

  /**
   * Fetch up to `maxRows` from this `cursor`, yielding a list of values and a boolean, `true`
   * if more rows are available, `false` otherwise.
   * @group Queries
   */
  def fetch(maxRows: Int): F[(List[A], Boolean)]

  /**
   * Transform this `Cursor` by a given `FunctionK`.
   * @group Transformations
   */
  def mapK[G[_]](fk: F ~> G): Cursor[G, A] =
    new Cursor[G, A] {
      def fetch(maxRows: Int): G[(List[A], Boolean)] = fk(outer.fetch(maxRows))
    }

}

/** @group Companions */
object Cursor {

  /**
   * `Cursor[F, *]` is a covariant functor if `F` is.
   * @group Typeclass Instances
   */
  implicit def functorCursor[F[_]: Functor]: Functor[Cursor[F, *]] =
    new Functor[Cursor[F, *]] {
      override def map[A, B](fa: Cursor[F, A])(f: A => B): Cursor[F, B] =
        new Cursor[F, B] {
          override def fetch(maxRows: Int): F[(List[B], Boolean)] =
            fa.fetch(maxRows).map {
              case (as, b) => (as.map(f), b)
            }
        }
    }

}