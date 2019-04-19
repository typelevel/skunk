// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import skunk.net.message._
import skunk.util.Namer

package object protocol {

  def exchange[F[_], A](fa: F[A])(
    implicit exchange: Exchange[F]
  ): F[A] = exchange(fa)

  def receive[F[_]](implicit ev: MessageSocket[F]): F[BackendMessage] =
    ev.receive

  def send[F[_], A: FrontendMessage](a: A)(implicit ev: MessageSocket[F]): F[Unit] =
    ev.send(a)

  def history[F[_]](max: Int)(implicit ev: MessageSocket[F]): F[List[Either[Any, Any]]] =
    ev.history(max)

  def expect[F[_], B](f: PartialFunction[BackendMessage, B])(implicit ev: MessageSocket[F]): F[B] =
    ev.expect(f)

  def flatExpect[F[_], B](f: PartialFunction[BackendMessage, F[B]])(implicit ev: MessageSocket[F]): F[B] =
    ev.flatExpect(f)

  def nextName[F[_]](prefix: String)(implicit ev: Namer[F]): F[String] =
    ev.nextName(prefix)

}