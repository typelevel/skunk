// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net

import skunk.net.message._
import skunk.util.Namer
import skunk.util.Origin
import skunk.util.Otel
import org.typelevel.otel4s.trace.Span
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.trace.SpanKind

package object protocol {

  def exchange[F[_]: Tracer, A](label: String)(f: Span[F] => F[A])(
    implicit exchange: Exchange[F]
  ): F[A] =
    Tracer[F].spanBuilder(label)
      .withSpanKind(SpanKind.Client)
      .addAttribute(Otel.DbSystemName)
      .withFinalizationStrategy(Otel.PostgresStrategy)
      .build
      .use(span => exchange(f(span)))

  def receive[F[_]](implicit ev: MessageSocket[F]): F[BackendMessage] =
    ev.receive

  def send[F[_]](message: FrontendMessage)(implicit ev: MessageSocket[F]): F[Unit] =
    ev.send(message)

  def history[F[_]](max: Int)(implicit ev: MessageSocket[F]): F[List[Either[Any, Any]]] =
    ev.history(max)

  def expect[F[_], B](f: PartialFunction[BackendMessage, B])(
    implicit ev: MessageSocket[F],
             or: Origin
  ): F[B] =
    ev.expect(f)

  def flatExpect[F[_], B](f: PartialFunction[BackendMessage, F[B]])(
    implicit ev: MessageSocket[F],
             or: Origin
  ): F[B] =
    ev.flatExpect(f)

  def nextName[F[_]](prefix: String)(implicit ev: Namer[F]): F[String] =
    ev.nextName(prefix)

}
