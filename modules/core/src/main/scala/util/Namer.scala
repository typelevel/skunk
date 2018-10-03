package skunk.util

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._

trait Namer[F[_]] {
  def nextName(prefix: String): F[String]
}

object Namer {

  def apply[F[_]: Sync]: F[Namer[F]] =
    Ref[F].of(1).map { ctr =>
      new Namer[F] {
        def nextName(prefix: String) =
          ctr.modify(n => (n + 1, s"$prefix-$n"))
      }
    }

}