// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.util

import cats.~>
import cats.effect.Sync
import cats.implicits._
import skunk.Statement
import java.{ util => ju }

/** An LRU (by access) cache, keyed by statement `CacheKey`. */
sealed trait StatementCache[F[_], V] { outer =>

  def get(k: Statement[_]): F[Option[V]]
  private[skunk] def put(k: Statement[_], v: V): F[Unit]
  def containsKey(k: Statement[_]): F[Boolean]
  def clear: F[Unit]

  def mapK[G[_]](fk: F ~> G): StatementCache[G, V] =
    new StatementCache[G, V] {
      def get(k: Statement[_]): G[Option[V]] = fk(outer.get(k))
      def put(k: Statement[_], v: V): G[Unit] = fk(outer.put(k, v))
      def containsKey(k: Statement[_]): G[Boolean] = fk(outer.containsKey(k))
      def clear: G[Unit] = fk(outer.clear)
    }

}

object StatementCache {

  /** Capability trait for constructing a `StatementCache`. */
  trait Make[F[_]] {

    /** Construct an empty `StatementCache` with the specified capacity. */
    def empty[V](max: Int): F[StatementCache[F, V]]

  }

  object Make {

    def apply[F[_]](implicit ev: Make[F]): ev.type = ev

    implicit def syncMake[F[_]: Sync]: Make[F] =
      new Make[F] {

        def empty[V](max: Int): F[StatementCache[F, V]] =
          Sync[F].delay(
            new ju.LinkedHashMap[Statement.CacheKey, V]() {
              override def removeEldestEntry(e: ju.Map.Entry[Statement.CacheKey, V]): Boolean =
                size > max
            }
          ).map { lhm =>
            new StatementCache[F, V] {
              def get(k: Statement[_]): F[Option[V]] = Sync[F].delay(Option(lhm.get(k.cacheKey)))
              def put(k: Statement[_], v: V): F[Unit] = Sync[F].delay(lhm.put(k.cacheKey, v)).void
              def containsKey(k: Statement[_]): F[Boolean] = Sync[F].delay(lhm.containsKey(k.cacheKey))
              val clear: F[Unit] = Sync[F].delay(lhm.clear())
            }
          }

      }

    }

}
