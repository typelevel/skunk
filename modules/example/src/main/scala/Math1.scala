// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.numeric.{ int4, float8 }
import org.typelevel.otel4s.trace.Tracer

object Math1 extends IOApp {

  implicit val tracer: Tracer[IO] = Tracer.noop

  val session: Resource[IO, Session[IO]] =
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .single

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

    // `Math` implementation that delegates its work to Postgres.
    def fromSession[F[_]](sess: Session[F])(
      implicit ev: MonadCancel[F, Throwable]
    ): Math[F] =
      new Math[F] {
        def add(a: Int, b: Int) = sess.prepare(Statements.add).flatMap(_.unique((a, b)))
        def sqrt(d: Double)     = sess.prepare(Statements.sqrt).flatMap(_.unique(d))
      }

  }

  def run(args: List[String]): IO[ExitCode] =
    (session.map(Math.fromSession(_)) : Resource[IO, Math[IO]]).use { m => // dotty requires the ascription, why?
      for {
        n  <- m.add(42, 71)
        d  <- m.sqrt(2)
        d2 <- m.sqrt(42)
        _  <- IO(println(s"The answers were $n and $d and $d2"))
      } yield ExitCode.Success
    }

}
