import cats.effect.Resource

/**
 * ==Skunk is a functional data access layer for Postgres.==
 *
 * Skunk differs from [[https://tpolecat.github.io/doobie/ doobie]] in a number of important ways:
 *
 *   - Skunk doesn't use JDBC at all. It speaks the Postgres wire protocol.
 *   - Skunk is asynchronous all the way down, via cats-effect, fs2, and ultimately nio.
 *   - Data mapping is simpler, explicit, and does not involve implicit derivation.
 *
 * @groupdesc HLists This idea was borrowed from scodec. We use `~` to build left-associated HLists
 *   of values and types, and can destructure with `~` the same way.
 *   {{{
 *   val a: Int ~ String ~ Boolean =
 *     1 ~ "foo" ~ true
 *
 *   a match {
 *     case n ~ s ~ b => ...
 *   }
 *   }}}
 *   Note that the `~` operation for `Codec`, `Encoder`, and `Decoder` is lifted. This is usually
 *   what you want. If you do need an HList of encoders you can use `Tuple2`.
 *   {{{
 *   val c: Encoder[Int ~ String ~ Boolean]
 *     int4 ~ bpchar ~ bit
 *
 *   // Unusual, but for completeness you can do it thus:
 *   val d: Encoder[Int] ~ Encoder[String] ~ Encoder[Boolean] =
 *     ((int4, bpchar), bit)
 *   }}}
 *
 * @groupdesc Codecs When you construct a statement each parameter is specified via an `Ecoder`, and
 *   row data is specified via a `Decoder`. In some cases encoders and decoders are symmetric and
 *   are defined together, as a `Codec`. This pattern is similar but less general than the one
 *   adopted by scodec.
 */
package object skunk {

  private[skunk] def void(a: Any): Unit = (a, ())._2

  /**
   * Infix alias for `(A, B)` that provides convenient syntax for left-associated HLists.
   * @group HLists
   */
  type ~[+A, +B] = (A, B)

  /**
   * Companion providing unapply for `~` such that `(x ~ y ~ z) match { case a ~ b ~ c => ... }`.
   * @group HLists
   */
  object ~ {
    def unapply[A, B](t: A ~ B): Some[A ~ B] = Some(t)
  }

  /**
   * @group Type Aliases
   */
  type SessionPool[F[_]] = Resource[F, Resource[F, Session[F]]]

  object implicits
    extends syntax.ToAllOps

}

