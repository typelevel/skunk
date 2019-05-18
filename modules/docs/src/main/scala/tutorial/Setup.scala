// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tutorial

//#hello
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._

object Hello extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(                                         // (1)
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world"
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>                                      // (2)
      for {
        s <- s.unique(sql"select xcurrent_date".query(date)) // (3)
        _ <- IO(println(s"The current date is $s."))        // (4)
      } yield ExitCode.Success
    }

}
//#hello