// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.syntax.all._
import skunk._, skunk.implicits._, skunk.codec.all.int4
import natchez.Trace.Implicits.noop
import cats.effect.std.Console

object Transaction extends IOApp {

  def session[F[_]: Async: Console]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  def runS[F[_]: Async: Console]: F[Int] =
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
