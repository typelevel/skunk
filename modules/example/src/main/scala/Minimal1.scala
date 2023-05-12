// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import org.typelevel.otel4s.trace.Tracer

object Minimal1 extends IOApp {

  implicit val tracer: Tracer[IO] = Tracer.noop[IO]

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        s <- s.unique(sql"select current_date".query(date))
        _ <- IO(println(s"⭐️⭐  The answer is $s."))
      } yield ExitCode.Success
    }

}
