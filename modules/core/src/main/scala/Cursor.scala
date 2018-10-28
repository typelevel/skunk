package skunk

import cats.Functor
import cats.implicits._

/**
 * A cursor from which rows can be fetched, valid within its defining `Session`.
 * @group Queries
 */
trait Cursor[F[_], A] { outer =>

  /**
   * Fetch the next `maxRows` from this `cursor`, yielding a list of values and a boolean, `true`
   * if more rows are available, `false` otherwise.
   * @group Queries
   */
  def fetch(maxRows: Int): F[(List[A], Boolean)]

}

object Cursor {

  /**
   * `Cursor[F, ?]` is a covariant functor if `F` is.
   * @group Typeclass Instances
   */
  implicit def functorCursor[F[_]: Functor]: Functor[Cursor[F, ?]] =
    new Functor[Cursor[F, ?]] {
      def map[A, B](fa: Cursor[F, A])(f: A => B) =
        new Cursor[F, B] {
          def fetch(maxRows: Int) =
            fa.fetch(maxRows).map {
              case (as, b) => (as.map(f), b)
            }
        }
    }

}