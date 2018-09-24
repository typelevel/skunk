package skunk

import cats.effect.Concurrent
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Signal
import skunk.proto._
import skunk.proto.message._

trait Session[F[_]] {
  def parameters: Signal[F, Map[String, String]]
  def query[A](sql: String): F[(RowDescription, List[RowData])]
  def startup(user: String, database: String): F[Unit]
  def transactionStatus: Signal[F, ReadyForQuery.Status]
  def listen(channel: String, maxQueued: Int): Stream[F, NotificationResponse]
}

object Session {

  def fromActiveMessageSocket[F[_]: Concurrent](ams: ActiveMessageSocket[F]): F[Session[F]] =
    Semaphore[F](1).map { sem =>
      new Session[F] {
        def parameters = ams.parameters
        def query[A](sql: String) = SimpleQuery.simple(ams, sem, Query(sql))
        def startup(user: String, database: String) = Startup(ams, sem, StartupMessage(user, database))
        def transactionStatus = ams.transactionStatus
        def listen(channel: String, maxQueued: Int) = Command.listen2(ams, sem, channel, maxQueued)
      }
    }

}