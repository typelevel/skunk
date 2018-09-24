package skunk
package proto

import cats.Monad
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.Stream
import skunk.proto.message.{ Query, CommandComplete, ReadyForQuery, NotificationResponse }

object Command {

  def listen2[F[_]: Monad, A](
    ams:       ActiveMessageSocket[F],
    sem:       Semaphore[F],
    channel:   String,
    maxQueued: Int
  ): Stream[F, NotificationResponse] =
    Stream.eval_ {
      sem.withPermit {
        for {
          _ <- ams.send(Query(s"LISTEN $channel"))
          _ <- ams.expect { case CommandComplete("LISTEN") => }
          _ <- ams.expect { case ReadyForQuery(_) => }
        } yield ()
      }
    } ++ ams.notifications(maxQueued).filter(_.channel === channel)

}

