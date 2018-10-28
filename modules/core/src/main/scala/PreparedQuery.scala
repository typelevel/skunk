package skunk

import cats.{ Contravariant, Functor, MonadError }
import cats.arrow.Profunctor
import cats.effect.Resource
import cats.implicits._
import fs2.{ Chunk, Stream }
import skunk.net.ProtoSession

/**
 * A prepared query, valid for the life of its originating `Session`.
 * @group Queries
 */
trait PreparedQuery[F[_], A, B] {

  /**
   * Check that this `PreparedQuery`'s asserted argument and result types align correctly with the
   * schema. In case of misalignment an exception is raised with a description of the problem.
   * @group Queries
   */
  def check: F[Unit]

  /**
   * Binds the supplied arguments to this `PreparedQuery`, yielding a `Cursor` from which
   * rows can be `fetch`ed. Note that higher-level operations like `stream`, `option`, and `unique`
   * are usually what you want.
   * @group Queries
   */
  def open(args: A): F[Cursor[F, B]]

  /**
   * Construct a stream that calls `fetch` repeatedly and emits chunks until none remain. Note
   * that each chunk is read atomically while holding the session mutex, which means interleaved
   * streams will achieve better fairness with smaller chunks but greater overall throughput with
   * larger chunks. So it's important to consider the use case when specifying `chunkSize`.
   * @group Queries
   */
  def stream(args: A, chunkSize: Int): Stream[F, B]

  /**
   * Fetch and return at most one row, raising an exception if more rows are available.
   * @group Queries
   */
  def option(args: A): F[Option[B]]

  /**
   * Fetch and return at most one row, if any.
   * @group Queries
   */
  def headOption(args: A): F[Option[B]]

  /**
   * Fetch and return exactly one row, raising an exception if there are more or fewer.
   * @group Queries
   */
  def unique(args: A): F[B]

}

object PreparedQuery {

  /**
   * `PreparedQuery[F, ?, B]` is a contravariant functor for all `F`.
   * @group Typeclass Instances
   */
  implicit def functorPreparedQuery[F[_]: Functor, A]: Functor[PreparedQuery[F, A, ?]] =
    new Functor[PreparedQuery[F, A, ?]] {
      def map[T, U](fa: PreparedQuery[F, A, T])(f: T => U) =
        new PreparedQuery[F, A, U] {
            def check = fa.check
            def open(args: A) = fa.open(args).map(_.map(f))
            def stream(args: A, chunkSize: Int) = fa.stream(args, chunkSize).map(f)
            def option(args: A) = fa.option(args).map(_.map(f))
            def headOption(args: A) = fa.headOption(args).map(_.map(f))
            def unique(args: A) = fa.unique(args).map(f)
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
            def check = fa.check
            def open(args: U) = fa.open(f(args))
            def stream(args: U, chunkSize: Int) = fa.stream(f(args), chunkSize)
            def option(args: U) = fa.option(f(args))
            def headOption(args: U) = fa.headOption(f(args))
            def unique(args: U) = fa.unique(f(args))
        }
    }

  /**
   * `PreparedQuery[F, ?, ?]` is a profunctor if `F` is a covariant functor.
   * @group Typeclass Instances
   */
  implicit def profunctorPreparedQuery[F[_]: Functor]: Profunctor[PreparedQuery[F, ?, ?]] =
    new Profunctor[PreparedQuery[F, ?, ?]] {
      def dimap[A, B, C, D](fab: PreparedQuery[F, A, B])(f: (C) ⇒ A)(g: (B) ⇒ D) =
        contravariantPreparedQuery[F, B].contramap(fab)(f).map(g) // y u no work contravariant syntax
      override def lmap[A, B, C](fab: PreparedQuery[F, A, B])(f: (C) ⇒ A) =
        contravariantPreparedQuery[F, B].contramap(fab)(f)
      override def rmap[A, B, C](fab: PreparedQuery[F, A, B])(f: (B) ⇒ C) = fab.map(f)
    }

  def fromQueryAndProtoSession[F[_]: MonadError[?[_], Throwable], A, B](query: Query[A, B], proto: ProtoSession[F]) =
    proto.prepare(query).map { pq =>
      new PreparedQuery[F, A, B] {

        def check =
          proto.check(pq)

        def open(args: A) =
          proto.bind(pq, args).map { p =>
            new Cursor[F, B] {
              def fetch(maxRows: Int) =
                proto.execute(p, maxRows)
            }
          }

        def stream(args: A, chunkSize: Int) = {
          val rsrc = Resource.make(proto.bind(pq, args))(proto.close)
          Stream.resource(rsrc).flatMap { cursor =>
            def chunks: Stream[F, B] =
              Stream.eval(proto.execute(cursor, chunkSize)).flatMap { case (bs, more) =>
                val s = Stream.chunk(Chunk.seq(bs))
                if (more) s ++ chunks
                else s
              }
            chunks
          }
        }

        // We have a few operations that only want the first row. In order to do this AND
        // know if there are more we need to ask for 2 rows.
        private def fetch2(args: A): F[(List[B], Boolean)] =
          open(args).flatMap(_.fetch(2))

        def option(args: A) =
          fetch2(args).flatMap { case (bs, _) =>
            bs match {
              case b :: Nil => b.some.pure[F]
              case Nil      => none[B].pure[F]
              case _        => MonadError[F, Throwable].raiseError(new RuntimeException("Expected exactly one result, more returned."))
            }
          }

        def headOption(args: A) =
          fetch2(args).map(_._1.headOption)

        def unique(args: A) =
          fetch2(args).flatMap { case (bs, _) =>
            bs match {
              case b :: Nil => b.pure[F]
              case Nil      => MonadError[F, Throwable].raiseError(new RuntimeException("Expected exactly one result, none returned."))
              case _        => MonadError[F, Throwable].raiseError(new RuntimeException("Expected exactly one result, more returned."))
            }
          }

      }

    }

}