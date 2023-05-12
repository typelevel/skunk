// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.Monad
import cats.effect.{ ExitCode, IO, IOApp, Resource }
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.numeric.{ int4, float8 }
import org.typelevel.otel4s.trace.Tracer

object Math2 extends IOApp {

  implicit val tracer: Tracer[IO] = Tracer.noop

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      debug    = true
    )

  // An algebra for doing math.
  trait Math[F[_]] {
    def add(a: Int, b: Int): F[Int]
    def sqrt(d: Double): F[Double]
  }

  object Math {

    object Statements {
      val add  = sql"select $int4 + $int4".query(int4)
      val sqrt = sql"select sqrt($float8)".query(float8)
    }

    def fromSession[F[_]: Monad](sess: Session[F]): F[Math[F]] =
      for {
        pAdd  <- sess.prepare(Statements.add)
        pSqrt <- sess.prepare(Statements.sqrt)
      } yield
        new Math[F] {
          def add(a: Int, b: Int) = pAdd.unique((a, b))
          def sqrt(d: Double)     = pSqrt.unique(d)
        }

  }

  def run(args: List[String]): IO[ExitCode] =
    session.evalMap(Math.fromSession(_)).use { m =>
      for {
        n  <- m.add(42, 71)
        d  <- m.sqrt(2)
        d2 <- m.sqrt(42)
        _  <- IO(println(s"The answers were $n and $d and $d2"))
      } yield ExitCode.Success
    }

}
