// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.syntax

import cats.{ ~>, Functor }
import cats.effect.Resource
import cats.effect.Resource.{ Allocate, Bind, Suspend }
import cats.implicits._

final class ResourceOps[F[_], A](rsrc: Resource[F, A]) {

  def mapK[G[_]](fk: F ~> G)(implicit F: Functor[F]): Resource[G, A] =
    ResourceOps.mapK(rsrc)(fk)

}

object ResourceOps {

  // Really we can implement this if either is a functor, so I flipped a coin.
  def mapK[F[_]: Functor, G[_], A](rsrc: Resource[F, A])(fk: F ~> G): Resource[G, A] =
    rsrc match {
      case Allocate(fa) => Allocate(fk(
        fa.map { case (a, f) => (a, f.map(fk(_))) }
      ))
      case Bind(s, fs) => Bind(mapK(s)(fk), (z: Any) => mapK(fs(z))(fk)) // wtf
      case Suspend(fr)  => Suspend(fk(fr.map(mapK(_)(fk))))
    }

}

trait ToResourceOps {
  implicit def toResourceOps[F[_], A](rsrc: Resource[F, A]): ResourceOps[F, A] =
    new ResourceOps(rsrc)
}

object resource extends ToResourceOps