// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.implicits._
import skunk._, skunk.implicits._, skunk.codec.all.int4
import natchez.Trace.Implicits.noop

object Transaction extends IOApp {

  def session[F[_]: Concurrent: ContextShift]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world"
    )

  def runS[F[_]: Concurrent: ContextShift]: F[_] =
    session[F].use { s =>
      s.transaction.use { t =>
        for {
          p <- t.savepoint
          _ <- s.execute(sql"blah".command).attempt
          _ <- t.rollback(p)
          n <- s.execute(sql"select 1".query(int4))
        } yield n
      }
    }

  def run(args: List[String]): IO[ExitCode] =
    runS[IO].attempt.as(ExitCode.Success)

}
