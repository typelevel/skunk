// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.{ Contravariant, Functor, ~> }
import cats.arrow.Profunctor
import cats.effect.{ Resource, MonadCancel }
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.all._
import fs2.{ Pipe, Stream }
import skunk.data.{ Identifier, Notification }
import skunk.net.Protocol
import skunk.util.Origin

/**
 * A '''channel''' that can be used for inter-process communication, implemented in terms of
 * `LISTEN` and `NOTIFY`. All instances start life as a `Channel[F, String, Notification]` but can
 * be mapped out to different input and output types. See the linked documentation for more
 * information on the transactional semantics of these operations.
 * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
 * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
 * @group Session
 */
trait Channel[F[_], A, B] extends Pipe[F, A, Unit] { outer =>

  /**
   * Construct a `Stream` that subscribes to notifications for this Channel, emits any notifications
   * that arrive (this can happen at any time), then unsubscribes when the stream is terminated.
   * Note that once such a stream is started it is important to consume all notifications as quickly
   * as possible to avoid blocking message processing for other operations on the `Session`
   * (although typically a dedicated `Session` will receive channel notifications so this won't be
   * an issue).
   * @param maxQueued the maximum number of notifications to hold in a queue before [semantically]
   *   blocking message exchange on the controlling `Session`.
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
   */
  def listen(maxQueued: Int): Stream[F, Notification[B]]

  /**
   * Construct a `Resource[F, Stream]` that subscribes to notifications for this Channel that emits any notifications
   * that arrive (this can happen at any time) once resource is acquired and unsubscribes when the resource is released.
   * Note that once such a stream is started it is important to consume all notifications as quickly
   * as possible to avoid blocking message processing for other operations on the `Session`
   * (although typically a dedicated `Session` will receive channel notifications so this won't be
   * an issue).
   *
   * @param maxQueued the maximum number of notifications to hold in a queue before [semantically]
   *                  blocking message exchange on the controlling `Session`.
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-listen.html LISTEN]]
   */
  def listenR(maxQueued: Int): Resource[F, Stream[F, Notification[B]]]

  /** This `Channel` acts as an fs2 `Pipe`. */
  def apply(sa: Stream[F, A]): Stream[F,Unit] =
    sa.evalMap(notify)

  /**
   * Send a notification on the given channel. Note that if the session is in an active transaction
   * the notification will only be sent if the transaction is committed. See the linked
   * documentation for more information.
   * @group Notifications
   * @see [[https://www.postgresql.org/docs/10/static/sql-notify.html NOTIFY]]
   */
  def notify(message: A): F[Unit]

  /**
   * Map notifications to a new type `D`, yielding an `Channel[D, A, D]`.
   * @group Transformations
   */
  def map[D](f: B => D): Channel[F, A, D] =
    dimap(identity[A])(f)

  /**
   * Contramap messages from a new type `C`, yielding an `Channel[D, C, B]`.
   * @group Transformations
   */
  def contramap[C](f: C => A): Channel[F, C, B] =
    dimap(f)(identity[B])

  /**
   * Contramap inputs from a new type `C` and map outputs to a new type `D`, yielding a
   * `Channel[F, C, D]`.
   *
   * @group Transformations
   */
  def dimap[C, D](f: C => A)(g: B => D): Channel[F, C, D] =
    new Channel[F, C, D] {
      def listen(maxQueued: Int): Stream[F, Notification[D]] = outer.listen(maxQueued).map(_.map(g))
      def notify(message: C): F[Unit] = outer.notify(f(message))
      def listenR(maxQueued: Int): Resource[F, Stream[F, Notification[D]]] = outer.listenR(maxQueued).map(_.map(_.map(g)))
    }

  /**
   * Transform this `Channel` by a given `FunctionK`.
   *
   * @group Transformations
   */
  def mapK[G[_]](fk: F ~> G)(implicit f: MonadCancel[F, _], g: MonadCancel[G, _]): Channel[G, A, B] =
    new Channel[G, A, B] {
      def listen(maxQueued: Int): Stream[G, Notification[B]] = outer.listen(maxQueued).translate(fk)
      def notify(message: A): G[Unit] = fk(outer.notify(message))
      def listenR(maxQueued: Int): Resource[G, Stream[G, Notification[B]]] = outer.listenR(maxQueued).map(_.translate(fk)).mapK(fk)
    }

}

/** @group Companions */
object Channel {

  /**
   * Construct a `Channel` given a name and an underlying `Protocol` (note that this is atypical;
   * normally a `Channel` is obtained from a `Session`).
   * @group Constructors
   */
  def fromNameAndProtocol[F[_]: MonadCancelThrow](name: Identifier, proto: Protocol[F]): Channel[F, String, String] =
    new Channel[F, String, String] {

    val listen: F[Unit] =
      proto.execute(Command(s"LISTEN ${name.value}", Origin.unknown, Void.codec)).void

    val unlisten: F[Unit] =
      proto.execute(Command(s"UNLISTEN ${name.value}", Origin.unknown, Void.codec)).void

    def listen(maxQueued: Int): Stream[F, Notification[String]] =
      for {
        _ <- Stream.resource(Resource.make(listen)(_ => unlisten))
        s <- Stream.resource(proto.notifications(maxQueued))
        n <- s.filter(_.channel === name)
      } yield n


    def listenR(maxQueued: Int): Resource[F, Stream[F, Notification[String]]] =
      Resource.make(listen)(_ => unlisten)
        .flatMap(_ => proto.notifications(maxQueued))
        .map(stream => stream.filter(_.channel === name))


      def notify(message: String): F[Unit] =
      // TODO: escape the message
      proto.execute(Command(s"NOTIFY ${name.value}, '$message'", Origin.unknown, Void.codec)).void

  }

  /**
   * `Channel[F, T, *]` is a covariant functor for all `F` and `T`.
   * @group Typeclass Instances
   */
  implicit def functorChannel[F[_], T]: Functor[Channel[F, T, *]] =
    new Functor[Channel[F, T, *]] {
      def map[A, B](fa: Channel[F, T, A])(f: A => B): Channel[F, T, B] =
        fa.map(f)
    }

  /**
   * `Channel[F, *, T]` is a contravariant functor for all `F` and `T`.
   * @group Typeclass Instances
   */
  implicit def contravariantChannel[F[_], T]: Contravariant[Channel[F, *, T]] =
    new Contravariant[Channel[F, *, T]] {
      def contramap[A, B](fa: Channel[F, A, T])(f: B => A): Channel[F, B, T] =
        fa.contramap(f)
    }

  /**
   * `Channel[F, *, *]` is a profunctor for all `F`.
   * @group Typeclass Instances
   */
  implicit def profunctorChannel[F[_]]: Profunctor[Channel[F, *, *]] =
    new Profunctor[Channel[F, *, *]] {
      def dimap[A, B, C, D](fab: Channel[F, A, B])(f: C => A)(g: B => D): Channel[F, C, D] =
        fab.dimap(f)(g)
    }

}