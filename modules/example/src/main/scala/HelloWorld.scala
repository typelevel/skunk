package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.numeric._

object HelloWorld extends IOApp {

  trait Math[F[_]] {
    def add(a: Int, b: Int): F[Int]
    def sqrt(d: Double): F[Double]
  }

  object Statements {
    val add  = sql"select $int4 + $int4".query(int4)
    val sqrt = sql"select sqrt($float8)".query(float8)
  }

  def mathSession[F[_]: Bracket[?[_], Throwable]](s: Session[F]): Math[F] =
    new Math[F] {
      def add(a: Int, b: Int) = s.prepare(Statements.add).use(s.unique(_, a ~ b))
      def sqrt(d: Double)     = s.prepare(Statements.sqrt).use(s.unique(_, d))
    }

  def session[F[_]: ConcurrentEffect]: Resource[F, Session[F]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  def math[F[_]: ConcurrentEffect]: Resource[F, Math[F]] =
    session.map(mathSession[F])

  def run(args: List[String]): IO[ExitCode] =
    math[IO].use { m =>
      for {
        n <- m.add(42, 71)
        d <- m.sqrt(2)
        _ <- IO(println(s"The answers were $n and $d"))
      } yield ExitCode.Success
    }

}