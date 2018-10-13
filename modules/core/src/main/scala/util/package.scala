package skunk

import cats.effect.Resource

package object util {

  type Pool[F[_], A] = Resource[F, Resource[F, A]]

}