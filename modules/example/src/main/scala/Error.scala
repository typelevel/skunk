// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import org.typelevel.otel4s.trace.Tracer

object Error extends IOApp {

  implicit val trace: Tracer[IO] = Tracer.noop

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  val query =
    sql"""
      SELECT name, code
      FROM   country
      WHERE  popsulation > $varchar
      AND    population  < $int4
    """.query(varchar *: int4)

  def prog[F[_]](s: Session[F])(implicit ev: MonadCancel[F, Throwable]): F[ExitCode] =
    s.prepare(query).flatMap(_.unique(("foo", 1000000))).as(ExitCode.Success)

  def run(args: List[String]): IO[ExitCode] =
    session.use(prog(_))

}
