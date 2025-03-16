// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.syntax.all._
import skunk._, skunk.implicits._, skunk.codec.all.int4
import org.typelevel.otel4s.trace.Tracer
import cats.effect.std.Console
import fs2.io.net.Network

object Transaction extends IOApp {

  implicit def tracer[F[_]: MonadCancelThrow]: Tracer[F] = Tracer.noop

  def session[F[_]: Temporal: Console: Network]: Resource[F, Session[F]] =
    Session.Builder[F]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .single

  def runS[F[_]: Temporal: Console: Network]: F[Int] =
    session[F].use { s =>
      s.transaction.use { t =>
        for {
          p <- t.savepoint
          _ <- s.execute(sql"blah".command).attempt
          _ <- t.rollback(p)
          n <- s.unique(sql"select 1".query(int4))
        } yield n
      }
    }

  def run(args: List[String]): IO[ExitCode] =
    runS[IO].as(ExitCode.Success)

}
