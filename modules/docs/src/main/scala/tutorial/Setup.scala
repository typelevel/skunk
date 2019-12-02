// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tutorial

//#hello
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop                          // (1)

object Hello extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(                                          // (2)
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>                                       // (3)
      for {
        s <- s.unique(sql"select current_date".query(date))  // (4)
        _ <- IO(println(s"The current date is $s."))
      } yield ExitCode.Success
    }

}
//#hello