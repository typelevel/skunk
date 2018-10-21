package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.Codec._

object HelloWorld extends IOApp {

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
        rs <- s.quick(sql"select 42".query(int4))
        _  <- rs.traverse(s => IO(println(s)))
      } yield ExitCode.Success
    }

}