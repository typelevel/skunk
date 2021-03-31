// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.arrow.Profunctor
import skunk.util.Origin
import skunk.util.Twiddler

/**
 * SQL, parameter encoder, and row decoder for a statement that returns rows. We assume that `sql`
 * has the same number of placeholders of the form `$1`, `$2`, etc., as the number of slots encoded
 * by `encoder`, that `sql` selects the same number of columns are the number of slots decoded by
 * `decoder`, and that the parameter and column types specified by `encoder` and `decoder` are
 * consistent with the schema. The `check` methods on [[skunk.Session Session]] provide a means to
 * verify this assumption.
 *
 * You can construct a `Query` directly, although it is more typical to use the `sql`
 * interpolator.
 *
 * {{{
 * sql"SELECT name, age FROM person WHERE age > $int2".query(varchar ~ int2) // Query[Short, String ~ Short]
 * }}}
 *
 * @param sql A SQL statement returning no rows.
 * @param origin  The `Origin` where the sql was defined, if any.
 * @param encoder An encoder for all parameters `$1`, `$2`, etc., in `sql`.
 * @param decoder A decoder for selected columns.
 *
 * @see [[skunk.syntax.StringContextOps StringContextOps]] for information on the `sql`
 *   interpolator.
 * @see [[skunk.Session Session]] for information on executing a `Query`.
 *
 * @group Statements
 */
final case class Query[A, B](
  override val sql:     String,
  override val origin:  Origin,
  override val encoder: Encoder[A],
  decoder: Decoder[B]
) extends Statement[A] {

  /**
   * Query is a profunctor.
   * @group Transformations
   */
  def dimap[C, D](f: C => A)(g: B => D): Query[C, D] =
    Query(sql, origin, encoder.contramap(f), decoder.map(g))

  /**
   * Query is a contravariant functor in `A`.
   * @group Transformations
   */
  def contramap[C](f: C => A): Query[C, B] =
    dimap[C, B](f)(identity)

  def gcontramap[C](implicit ev: Twiddler.Aux[C, A]): Query[C, B] =
    contramap(ev.to)

  /**
   * Query is a covariant functor in `B`.
   * @group Transformations
   */
  def map[D](g: B => D): Query[A, D] =
    dimap[A, D](identity)(g)

  def gmap[D](implicit ev: Twiddler.Aux[D, B]): Query[A, D] =
    map(ev.from)

}

/** @group Companions */
object Query {

  implicit val ProfunctorQuery: Profunctor[Query] =
    new Profunctor[Query] {
      override def dimap[A, B, C, D](fab: Query[A,B])(f: C => A)(g: B => D): Query[C, D] =
        fab.dimap(f)(g)
    }

}