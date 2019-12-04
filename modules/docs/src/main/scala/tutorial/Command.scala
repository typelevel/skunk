// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tutorial
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.Resource
import skunk.data.Completion

object Command extends IOApp {

  //#command-a
  import cats.effect.IO
  import skunk._
  import skunk.implicits._
  import skunk.codec.all._
  import natchez.Trace.Implicits.noop

  val a: Command[Void] =
    sql"SET SEED TO 0.123".command
  //#command-a

  def q_a_exec[F[_]](s: Session[IO]): IO[Completion] = {
    //#command-a-exec
    // assume s: Session[IO]
    s.execute(a) // IO[Completion]
    //#command-a-exec
  }

  //#command-b
  val b: Command[Void] =
    sql"DELETE FROM country WHERE name = 'xyzzy'".command
  //#command-b

  //#command-c
  val c: Command[String] =
    sql"DELETE FROM country WHERE name = $varchar".command
  //#command-c

  //#command-d
  def update: Command[String ~ String] =
    sql"""
      UPDATE country
      SET    headofstate = $varchar
      WHERE  code = ${bpchar(3)}
    """.command
  //#command-d

  //#command-e
  case class Info(code: String, hos: String)

  def update2: Command[Info] =
    sql"""
      UPDATE country
      SET    headofstate = $varchar
      WHERE  code = ${bpchar(3)}
    """.command
       .contramap { case Info(code, hos) => code ~ hos }
  //#command-e

  //#command-f
  def update3: Command[Info] =
    sql"""
      UPDATE country
      SET    headofstate = $varchar
      WHERE  code = ${bpchar(3)}
    """.command
       .gcontramap[Info]
  //#command-f

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        _ <- s.execute(a)
        _ <- s.execute(b).flatMap(a => IO(println(a)))
        _ <- s.prepare(c).use(_.execute("xyzzy"))
        _ <- s.prepare(update).use(_.execute("Bob Dobbs" ~ "USA"))
      } yield ExitCode.Success
    }

}


