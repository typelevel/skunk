package tutorial

//#hello
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.temporal.date

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
        s <- s.unique(sql"select current_date".query(date)) // (3)
        _ <- IO(println(s"The current date is $s."))        // (4)
      } yield ExitCode.Success
    }

}
//#hello