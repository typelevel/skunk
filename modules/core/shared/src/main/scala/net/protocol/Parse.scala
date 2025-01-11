// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.protocol

import cats._
import cats.effect.Ref
import cats.syntax.all._
import skunk.util.StatementCache
import skunk.net.Protocol.StatementId

object Parse {

  /** A cache for the `Parse` protocol. */
  final case class Cache[F[_]](value: StatementCache[F, StatementId]) {
    def mapK[G[_]](fk: F ~> G): Cache[G] =
      Cache(value.mapK(fk))
  }

  object Cache {
    def empty[F[_]: Functor: Ref.Make](capacity: Int): F[Cache[F]] =
      StatementCache.empty[F, StatementId](capacity, true).map(Parse.Cache(_))
  }

}
