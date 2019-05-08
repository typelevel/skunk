// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.arrow.Profunctor
import cats.effect._
import cats.implicits._
import fs2.{ Chunk, Stream }
import skunk.exception.SkunkException
import skunk.net.Protocol
import skunk.util.{ CallSite, Origin }

/**
 * A prepared query, valid for the life of its originating `Session`.
 * @group Session
 */
trait PreparedQuery[F[_], A, B] {

  /**
   * `Resource` that binds the supplied arguments to this `PreparedQuery`, yielding a `Cursor` from
   * which rows can be `fetch`ed. Note that higher-level operations like `stream`, `option`, and
   * `unique` are usually what you want.
   */
  def cursor(args: A)(implicit or: Origin): Resource[F, Cursor[F, B]]

  /**
   * Construct a `Cursor`-backed stream that calls `fetch` repeatedly and emits chunks until none
   * remain. Note that each chunk is read atomically while holding the session mutex, which means
   * interleaved streams will achieve better fairness with smaller chunks but greater overall
   * throughput with larger chunks. So it's important to consider the use case when specifying
   * `chunkSize`.
   */
  def stream(args: A, chunkSize: Int)(implicit or: Origin): Stream[F, B]

  /**
   * Fetch and return at most one row, raising an exception if more rows are available.
   */
  def option(args: A)(implicit or: Origin): F[Option[B]]

  /**
   * Fetch and return exactly one row, raising an exception if there are more or fewer.
   */
  def unique(args: A)(implicit or: Origin): F[B]

}

/** @group Companions */
object PreparedQuery {

  def fromProto[F[_]: Bracket[?[_], Throwable], A, B](proto: Protocol.PreparedQuery[F, A, B]) =
    new PreparedQuery[F, A, B] {

      def cursor(args: A)(implicit or: Origin): Resource[F, Cursor[F, B]] =
        proto.bind(args, or).map { p =>
          new Cursor[F, B] {
            def fetch(maxRows: Int) =
              p.execute(maxRows)
          }
        }

      def stream(args: A, chunkSize: Int)(implicit or: Origin) =
        Stream.resource(proto.bind(args, or)).flatMap { cursor =>
          def chunks: Stream[F, B] =
            Stream.eval(cursor.execute(chunkSize)).flatMap { case (bs, more) =>
              val s = Stream.chunk(Chunk.seq(bs))
              if (more) s ++ chunks
              else s
            }
          chunks
        }

      // We have a few operations that only want the first row. In order to do this AND
      // know if there are more we need to ask for 2 rows.
      private def fetch2(args: A)(implicit or: Origin): F[(List[B], Boolean)] =
        cursor(args).use(_.fetch(2))

      def option(args: A)(implicit or: Origin) =
        fetch2(args).flatMap { case (bs, _) =>
          bs match {
            case b :: Nil => b.some.pure[F]
            case Nil      => none[B].pure[F]
            case _        =>
              fail("option", args, or,
                "Expected at most one result, more returned.",
                s"Did you mean to use ${framed("stream")}?"
              )
          }
        }

      def unique(args: A)(implicit or: Origin) =
        fetch2(args).flatMap { case (bs, _) =>

          bs match {
            case b :: Nil => b.pure[F]
            case Nil      =>
              fail(
                method = "unique",
                args   = args,
                or     = or,
                m      = "Exactly one row was expected, but none were returned.",
                h      = s"You used ${framed("unique")}. Did you mean to use ${framed("option")}?"
              )
            case _        =>
              fail("unique", args, or,
                "Exactly one row was expected, but more were returned.",
                s"You used ${framed("unique")}. Did you mean to use ${framed("stream")}?"
              )
            }
          }

      private def framed(s: String) =
        "\u001B[4m" + s + "\u001B[24m"

      private def fail[T](method: String, args: A, or: Origin, m: String, h: String): F[T] =
        MonadError[F, Throwable].raiseError[T] {
          SkunkException.fromQueryAndArguments(
            message    = m,
            query      = proto.query,
            args       = args,
            callSite   = Some(CallSite(method, or)),
            hint       = Some(h),
            argsOrigin = Some(or)
          )
        }

    }

  /**
   * `PreparedQuery[F, ?, B]` is a covariant functor when `F` is a monad.
   * @group Typeclass Instances
   */
  implicit def functorPreparedQuery[F[_]: Monad, A]: Functor[PreparedQuery[F, A, ?]] =
  new Functor[PreparedQuery[F, A, ?]] {
    def map[T, U](fa: PreparedQuery[F, A, T])(f: T => U) =
      new PreparedQuery[F, A, U] {
        def cursor(args: A)(implicit or: Origin) = fa.cursor(args).map(_.map(f))
        def stream(args: A, chunkSize: Int)(implicit or: Origin) = fa.stream(args, chunkSize).map(f)
        def option(args: A)(implicit or: Origin) = fa.option(args).map(_.map(f))
        def unique(args: A)(implicit or: Origin) = fa.unique(args).map(f)
      }
  }

  /**
   * `PreparedQuery[F, ?, B]` is a contravariant functor for all `F`.
   * @group Typeclass Instances
   */
  implicit def contravariantPreparedQuery[F[_], B]: Contravariant[PreparedQuery[F, ?, B]] =
    new Contravariant[PreparedQuery[F, ?, B]] {
      def contramap[T, U](fa: PreparedQuery[F, T, B])(f: U => T) =
        new PreparedQuery[F, U, B] {
          def cursor(args: U)(implicit or: Origin) = fa.cursor(f(args))
          def stream(args: U, chunkSize: Int)(implicit or: Origin) = fa.stream(f(args), chunkSize)
          def option(args: U)(implicit or: Origin) = fa.option(f(args))
          def unique(args: U)(implicit or: Origin) = fa.unique(f(args))
        }
    }

  /**
   * `PreparedQuery[F, ?, ?]` is a profunctor when `F` is a monad.
   * @group Typeclass Instances
   */
  implicit def profunctorPreparedQuery[F[_]: Monad]: Profunctor[PreparedQuery[F, ?, ?]] =
    new Profunctor[PreparedQuery[F, ?, ?]] {
      def dimap[A, B, C, D](fab: PreparedQuery[F, A, B])(f: (C) ⇒ A)(g: (B) ⇒ D) =
        contravariantPreparedQuery[F, B].contramap(fab)(f).map(g) // y u no work contravariant syntax
      override def lmap[A, B, C](fab: PreparedQuery[F, A, B])(f: (C) ⇒ A) =
        contravariantPreparedQuery[F, B].contramap(fab)(f)
      override def rmap[A, B, C](fab: PreparedQuery[F, A, B])(f: (B) ⇒ C) = fab.map(f)
    }

  implicit class PreparedQueryOps[F[_], A, B](outer: PreparedQuery[F, A, B]) {

    /**
     * Transform this `PreparedQuery` by a given `FunctionK`.
     * @group Transformations
     */
    def mapK[G[_]: Applicative: Defer](fk: F ~> G)(
      implicit ev: Bracket[F, Throwable]
    ): PreparedQuery[G, A, B] =
      new PreparedQuery[G, A, B] {
        def cursor(args: A)(implicit or: Origin): Resource[G,Cursor[G,B]] = outer.cursor(args).mapK(fk).map(_.mapK(fk))
        def option(args: A)(implicit or: Origin): G[Option[B]] = fk(outer.option(args))
        def stream(args: A, chunkSize: Int)(implicit or: Origin): Stream[G,B] = outer.stream(args, chunkSize).translate(fk)
        def unique(args: A)(implicit or: Origin): G[B] = fk(outer.unique(args))
      }

  }

}