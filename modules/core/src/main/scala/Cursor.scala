package skunk

import cats.Functor
import cats.implicits._

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

}

/** @group Companions */
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