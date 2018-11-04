// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.text._

object Minimal extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        s <- s.unique(sql"select 'hello world'".query(varchar))
        _ <- IO(println(s"The answer is '$s'."))
      } yield ExitCode.Success
    }

}