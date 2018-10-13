
import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.Codec._

object HelloWorld extends IOApp {

  // You need ConcurrentEffect[IO] to do this. You get it for free within IOApp.
  val pool = Session.pool[IO]("localhost", 5432, "postgres", "world", 10)

  def run(args: List[String]): IO[ExitCode] =
    pool.use { p =>
      p.use { s =>
        for {
          rs <- s.quick(sql"select 42".query(int4))
          _  <- rs.traverse(s => IO(println(s)))
        } yield ExitCode.Success
      }
    }

}