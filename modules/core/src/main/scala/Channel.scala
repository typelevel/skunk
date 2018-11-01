package skunk

import cats.{ Contravariant, Functor }
import cats.arrow.Profunctor
import cats.effect.Resource
import cats.implicits._
import fs2.Stream
import skunk.data.{ Identifier, Notification }
import skunk.proto.Protocol

/**
 * @group Session
 */
trait Channel[F[_], A, B] { outer =>

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
  def listen(maxQueued: Int): Stream[F, B]

  /**
   * Send a notification on the given channel.
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def notify(message: A): F[Unit]

  def map[D](f: B => D): Channel[F, A, D] =
    new Channel[F, A, D] {
      def listen(maxQueued: Int) = outer.listen(maxQueued).map(f)
      def notify(message: A) = outer.notify(message)
    }

  def contramap[C](f: C => A): Channel[F, C, B] =
    new Channel[F, C, B] {
      def listen(maxQueued: Int) = outer.listen(maxQueued)
      def notify(message: C) = outer.notify(f(message))
    }

  def dimap[C, D](f: C => A)(g: B => D): Channel[F, C, D] =
    new Channel[F, C, D] {
      def listen(maxQueued: Int) = outer.listen(maxQueued).map(g)
      def notify(message: C) = outer.notify(f(message))
    }

}

/** @group Companions */
object Channel {

  /** @group Constructors */
  def fromNameAndProtocol[F[_]: Functor](name: Identifier, proto: Protocol[F]): Channel[F, String, Notification] =
    new Channel[F, String, Notification] {

    val listen: F[Unit] =
      proto.quick(Command(s"LISTEN ${name.value}", Void.codec)).void

    val unlisten: F[Unit] =
      proto.quick(Command(s"UNLISTEN ${name.value}", Void.codec)).void

    def listen(maxQueued: Int) =
      for {
        _ <- Stream.resource(Resource.make(listen)(_ => unlisten))
        n <- proto.notifications(maxQueued).filter(_.channel === name)
      } yield n

    def notify(message: String) =
      // TODO: escape the message
      proto.quick(Command(s"NOTIFY ${name.value}, '$message'", Void.codec)).void

  }

  /**
   * `Channel[F, T, ?]` is a covariant functor for all `F` and `T`.
   * @group Typeclass Instances
   */
  implicit def functorChannel[F[_], T]: Functor[Channel[F, T, ?]] =
    new Functor[Channel[F, T, ?]] {
      def map[A, B](fa: Channel[F, T, A])(f: A => B) =
        fa.map(f)
    }

  /**
   * `Channel[F, ?, T]` is a contravariant functor for all `F` and `T`.
   * @group Typeclass Instances
   */
  implicit def contravariantChannel[F[_], T]: Contravariant[Channel[F, ?, T]] =
    new Contravariant[Channel[F, ?, T]] {
      def contramap[A, B](fa: Channel[F, A, T])(f: B => A) =
        fa.contramap(f)
    }

  /**
   * `Channel[F, ?, ?]` is a profunctor for all `F`.
   * @group Typeclass Instances
   */
  implicit def profunctorChannel[F[_]]: Profunctor[Channel[F, ?, ?]] =
    new Profunctor[Channel[F, ?, ?]] {
      def dimap[A, B, C, D](fab: Channel[F, A, B])(f: C => A)(g: B => D) =
        fab.dimap(f)(g)
    }

}