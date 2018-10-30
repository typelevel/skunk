package skunk

import cats.Functor
import cats.effect.Resource
import cats.implicits._
import fs2.Stream
import skunk.data.{ Identifier, Notification }
import skunk.net.ProtoSession

trait Channel[F[_]] {

  /**
   * Construct a `Stream` that subscribes to notifications for this Channel, emits any notifications
   * that arrive (this can happen at any time), then unsubscribes when the stream is terminated.
   * Note that once such a stream is started it is important to consume all notifications as quickly
   * as possible to avoid blocking message processing for other operations on the `Session`
   * (although typically a dedicated `Session` will receive channel notifications so this won't be
   * an issue).
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def listen(maxQueued: Int): Stream[F, Notification]

  /**
   * Send a notification on the given channel.
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def notify(message: String): F[Unit]

}

object Channel {

  def fromNameAndProtoSession[F[_]: Functor](name: Identifier, proto: ProtoSession[F]): Channel[F] =
    new Channel[F] {

      def listen(maxQueued: Int) =
        for {
          _ <- Stream.resource(Resource.make(proto.listen(name))(_ => proto.unlisten(name)))
          n <- proto.notifications(maxQueued).filter(_.channel === name)
        } yield n

      def notify(message: String) =
        proto.notify(name, message)

    }

}