// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.arrow.Profunctor
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.twiddles.Iso
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
 * sql"SELECT name, age FROM person WHERE age > $int2".query(varchar *: int2) // Query[Short, (String, Short)]
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
  decoder: Decoder[B],
  isDynamic: Boolean = false,
  override val telemetry: Statement.Telemetry = Statement.Telemetry.empty,
) extends Statement[A] {

  /** Attaches a low-cardinality summary used as `db.query.summary` and as the span name. Statement
    * summaries take precedence over summaries returned by a configured query analyzer. The
    * method is a shortcut for `addAttributes(DbAttributes.DbQuerySummary(summary))`.
    */
  def withQuerySummary(summary: String): Query[A, B] =
    copy(telemetry = telemetry.withQuerySummary(summary))

  /** Replaces the additional attributes exported on the logical database span. */
  def withAttributes(attributes: Attributes): Query[A, B] =
    copy(telemetry = telemetry.withAttributes(attributes))

  /** Adds or replaces additional logical database span attributes by key. */
  def addAttributes(attributes: Attribute[_]*): Query[A, B] =
    copy(telemetry = telemetry.addAttributes(attributes: _*))

  /**
   * Query is a profunctor.
   * @group Transformations
   */
  def dimap[C, D](f: C => A)(g: B => D): Query[C, D] =
    Query(sql, origin, encoder.contramap(f), decoder.map(g), isDynamic, telemetry)

  /**
   * Query is a contravariant functor in `A`.
   * @group Transformations
   */
  def contramap[C](f: C => A): Query[C, B] =
    dimap[C, B](f)(identity)

  @deprecated("Use .contrato[CaseClass] instead of .gcontramap[CaseClass]", "0.6")
  def gcontramap[C](implicit ev: Twiddler.Aux[C, A]): Query[C, B] =
    contramap(ev.to)

  def contrato[C](implicit ev: Iso[A, C]): Query[C, B] =
    contramap(ev.from)

  /**
   * Query is a covariant functor in `B`.
   * @group Transformations
   */
  def map[D](g: B => D): Query[A, D] =
    dimap[A, D](identity)(g)

  @deprecated("Use query(a *: b * :c).to[CaseClass] instead of query(a ~ b ~ c).gmap[CaseClass]", "0.6")
  def gmap[D](implicit ev: Twiddler.Aux[D, B]): Query[A, D] =
    map(ev.from)

  def to[D](implicit ev: Iso[B, D]): Query[A, D] =
    map(ev.to)

  def cacheKey: Statement.CacheKey =
    Statement.CacheKey(sql, encoder.types, decoder.types)

}

/** @group Companions */
object Query {

  implicit val ProfunctorQuery: Profunctor[Query] =
    new Profunctor[Query] {
      override def dimap[A, B, C, D](fab: Query[A,B])(f: C => A)(g: B => D): Query[C, D] =
        fab.dimap(f)(g)
    }

}
