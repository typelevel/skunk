package example

import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.numeric._

object Prepared extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  object MathStatements {
    val add  = sql"select $int4 + $int4".query(int4)
    val sqrt = sql"select sqrt($float8)".query(float8)
  }

  trait Math[F[_]] {
    def add(a: Int, b: Int): F[Int]
    def sqrt(d: Double): F[Double]
  }

  /**
   * Prepare all the statements up front, so at "runtime" all we have to do is bind and execute.
   * This might be useful if you know you will be using a statement more than once.
   */
  val math: Resource[IO, Math[IO]] =
    for {
      sess  <- session
      pAdd  <- Resource.liftF(sess.prepare(MathStatements.add))
      pSqrt <- Resource.liftF(sess.prepare(MathStatements.sqrt))
    } yield
      new Math[IO] {
        def add(a: Int, b: Int) = pAdd.unique(a ~ b)
        def sqrt(d: Double)     = pSqrt.unique(d)
      }

  def run(args: List[String]): IO[ExitCode] =
    math.use { m =>
      for {
        _  <- IO(println(s"---\nStarting the main program now."))
        n  <- m.add(42, 71)
        d  <- m.sqrt(2)
        d2 <- m.sqrt(42)
        _  <- IO(println(s"The answers were $n and $d and $d2"))
      } yield ExitCode.Success
    }

}