package skunk

import cats.effect._
import cats.implicits._
import fs2._
import fs2.Sink.showLinesStdOut
import shapeless._

object Main extends IOApp {
  import Codec._

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  val anyLinesStdOut: Sink[IO, Any] =
    _.map(_.toString).to(showLinesStdOut)

  val q: Query[Int :: HNil, (Option[Int], Int)] =
    sql"""
      SELECT indepyear, population
      FROM   country
      WHERE  population < $int4
    """.query(int4.opt ~ int4)

  def run2: Stream[IO, (Option[Int], Int)] =
    for {
      s  <- Stream.resource(Session[IO]("localhost", 5432))
      _  <- Stream.eval(s.startup("postgres", "world"))
      ps <- Stream.eval(s.prepare(q))
      a  <- s.execute(ps)(42)
    } yield a

  def run(args: List[String]): IO[ExitCode] =
    Session[IO]("localhost", 5432).use { s =>
      for {
        _   <- s.parameters.discrete.map(m => ">>>> " + m.get("client_encoding")).changes.to(anyLinesStdOut).compile.drain.start
        _   <- s.startup("postgres", "world")
        st  <- s.transactionStatus.get
        enc <- s.parameters.get.map(_.get("client_encoding"))
        _   <- putStrLn(s"Logged in! Transaction status is $st and client_encoding is $enc")
        _   <- s.listen("foo", 10).to(anyLinesStdOut).compile.drain.start
        rs  <- s.quick(sql"select indepyear, population from country limit 20".query(int2.opt ~ int4))
        _   <- rs.traverse(putStrLn)
        _   <- s.quick(sql"set seed = 0.123".command)
        _   <- s.quick(sql"set client_encoding = 'ISO8859_1'".command)
        _   <- s.quick(sql"set client_encoding = 'UTF8'".command)
        _   <- s.notify("foo", "here is a message")
        // _   <- s.query("select 'föf'").flatMap(report)
        _  <- s.prepare(q)
        _  <- s.prepare(sql"delete from country where population < $int2".command)
        _  <- putStrLn("Done.")
      } yield ExitCode.Success
    }

}
