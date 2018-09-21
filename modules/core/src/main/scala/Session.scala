package skunk

import fs2.Stream
import skunk.proto.message._

trait Session[F[_]] {
  def startup(user: String, database: String): Stream[F, Unit]
  def query[A](sql: String, handler: RowDescription => RowData => A): Stream[F, A]
  def terminate: Stream[F, Unit]
}
