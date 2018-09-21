package skunk

import cats.effect.Sync
import fs2.Stream
import fs2.concurrent.Signal
import skunk.proto._
import skunk.proto.message._

trait Session[F[_]] {
  def notifications(maxQueued: Int): Stream[F, Notification]
  def parameters: Signal[F, Map[String, String]]
  def query[A](sql: String, handler: RowDescription => RowData => A): Stream[F, A]
  def startup(user: String, database: String): F[Unit]
  def transactionStatus: Signal[F, ReadyForQuery.Status]
}

object Session {

  def fromActiveMessageSocket[F[_]: Sync](ams: ActiveMessageSocket[F]): Session[F] =
    new Session[F] {
      def notifications(maxQueued: Int) = ams.notifications(maxQueued)
      def parameters = ams.parameters
      def query[A](sql: String, handler: RowDescription => RowData => A) = SimpleQuery.query(ams, Query(sql), handler)
      def startup(user: String, database: String) = Startup(ams, StartupMessage(user, database))
      def transactionStatus = ams.transactionStatus
    }

}