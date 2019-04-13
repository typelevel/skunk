package skunk.net.protocol

trait Exchange[F[_]] {
  def apply[A](fa: F[A]): F[A]
}

