// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect.Resource
import org.typelevel.scalaccompat.annotation._
import org.typelevel.twiddles.TwiddleCompat

/**
 * '''Skunk''' is a functional data access layer for Postgres.
 *
 * Design principles:
 *
 *   - Skunk doesn't use JDBC. It speaks the Postgres wire protocol. It will not work with any other
 *     database back end.
 *   - Skunk is asynchronous all the way down, via cats-effect, fs2, and ultimately nio. The
 *     high-level network layers (`Protocol` and `Session`) are safe to use concurrently.
 *   - Serialization to and from schema types is not typeclass-based, so there are no implicit
 *     derivations. Codecs are explicit, like parser combinators.
 *   - I'm not sweating arity abstraction that much. Pass `a ~ b ~ c` for three args and `Void` if
 *     there are no args. This may change in the future but it's fine for now.
 *   - Skunk uses `Resource` for lifetime-managed objects, which means it takes some discipline to
 *     avoid leaks, especially when working concurrently. May or may not end up being problematic.
 *   - I'm trying to write good Scaladoc this time.
 *
 * A minimal example follows. We construct a `Resource` that yields a `Session`, then use it.
 *
 * {{{
 * package example
 *
 * import cats.effect._
 * import skunk._
 * import skunk.implicits._
 * import skunk.codec.numeric._
 *
 * object Minimal extends IOApp {
 *
 *   val session: Resource[IO, Session[IO]] =
 *     Session.single(
 *       host     = "localhost",
 *       port     = 5432,
 *       user     = "postgres",
 *       database = "world",
 *     )
 *
 *   def run(args: List[String]): IO[ExitCode] =
 *     session.use { s =>
 *       for {
 *         n <- s.unique(sql"select 42".query(int4))
 *         _ <- IO(println(s"The answer is $n."))
 *       } yield ExitCode.Success
 *     }
 *
 * }
 * }}}
 *
 * Continue reading for an overview of the library. It's pretty small.
 *
 * @groupprio Statements 10
 * @groupname Statements Queries and Commands
 * @groupdesc Statements  Skunk recognizes two classes of statements: `Query`, for statements
 *   that return rows; and `Command`, for statements that do not return rows. These values can be
 *   constructed directly but typically arise via the `sql` interpolator.
 *   {{{
 *   val q = sql"""
 *     SELECT id, name
 *     FROM   employee
 *     WHERE  age > $int2
 *   """.query(int4 ~ varchar) // Query[Short, Long ~ String]
 *   }}}
 *   In the above example note that query parameters are specified by interpolated `Encoder`s and
 *   column types are specified by a `Decoder` passed to `.query`. The `~` syntax constructs
 *   left-associated HLists of types and values via nested pairs. These are all described in more
 *   detail a bit further down.
 *
 *   Commands are constructed in a similar way but have no output columns and thus no `Decoder` is
 *   needed.
 *   {{{
 *   val c = sql"""
 *     UPDATE employee
 *     SET    salary = salary * 1.05
 *     WHERE  id = $int8
 *   """.command // Command[Long]
 *   }}}
 *   The interpolator also permits nested `Fragment`s and interpolated constants like table names.
 *   See `StringContextOps` for more information on the interpolator.
 *
 * @groupprio Session 20
 * @groupname Session Session Values
 * @groupdesc Session Skunk's central abstraction is the `Session`, which represents a connection
 *   to Postgres. From the `Session` we can produce prepared statements, cursors, and other
 *   resources that ultimately depend on their originating `Session`.
 *
 * @groupprio Codecs 30
 * @groupdesc Codecs When you construct a statement each parameter is specified via an `Encoder`, and
 *   row data is specified via a `Decoder`. In some cases encoders and decoders are symmetric and
 *   are defined together, as a `Codec`. There are many variants of this pattern in functional
 *   Scala libraries; this is closest in spirit to the strategy adopted by scodec.
 *
 * @groupprio HLists 40
 * @groupdesc HLists This idea was borrowed from scodec. We use `~` to build left-associated nested
 *   pairs of values and types, and can destructure with `~` the same way.
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
 *   It is possible that we will end up switching to `shapeless.HList` but this is good for now.
 *
 * @groupdesc Companions Companion objects for the traits and classes above.
 * @groupname Companions Companion Objects
 * @groupprio Companions 999
 */
@nowarn213("msg=package object inheritance is deprecated")
object `package` extends TwiddleCompat { // aka package object skunk, yes this actually works ...

  // we can use this to defeat value discarding warnings for erasable proof terms
  private[skunk] def void(a: Any*): Unit = (a, ())._2

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

  type SessionPool[F[_]] = Resource[F, Resource[F, Session[F]]]

  type Strategy = skunk.util.Typer.Strategy
  val  Strategy = skunk.util.Typer.Strategy

  object implicits
    extends syntax.ToAllOps

}

