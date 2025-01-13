// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats._
import cats.syntax.all._
import cats.effect.Ref
import skunk.util.StatementCache
import skunk.data.TypedRowDescription

object Describe {

  /** A cache for the `Describe` protocol. */
  final case class Cache[F[_]](
    commandCache: StatementCache[F, Unit],
    queryCache:   StatementCache[F, TypedRowDescription],
  ) {
    def mapK[G[_]](fk: F ~> G): Cache[G] =
      Cache(commandCache.mapK(fk), queryCache.mapK(fk))
  }

  object Cache {
    def empty[F[_]: Functor: Semigroupal: Ref.Make](
      commandCapacity: Int,
      queryCapacity:   Int,
    ): F[Cache[F]] = (
      StatementCache.empty[F, Unit](commandCapacity, false),
      StatementCache.empty[F, TypedRowDescription](queryCapacity, false)
    ).mapN(Describe.Cache(_, _))
  }

}
