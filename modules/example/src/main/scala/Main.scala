package skunk

import cats.effect._
import cats.implicits._
import fs2._
import fs2.Sink.showLinesStdOut
// import scala.concurrent.duration._

object Main extends IOApp {
  import Codec._

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  val anyLinesStdOut: Sink[IO, Any] =
    _.map(_.toString).to(showLinesStdOut)

  case class Country(name: String, code: String, indepyear: Option[Short], population: Int)
  val country: Codec[Country] =
    (varchar, bpchar, int2.opt, int4).imapN(Country.apply)(Country.unapply(_).get)

  val q: Query[Int, Country] =
    sql"""
      SELECT name, code, indepyear, population
      FROM   country
      WHERE  population < $int4
    """.query(country)

  def clientEncodingChanged(enc: String): IO[Unit] =
    putStrLn(s">>>> CLIENT ENCODING IS NOW: $enc")

  def run(args: List[String]): IO[ExitCode] =
    Session[IO]("localhost", 5432).use { s =>
      for {
        _   <- s.startup("postgres", "world")
        _   <- s.parameter("client_encoding").evalMap(clientEncodingChanged).compile.drain.start
        st  <- s.transactionStatus.get
        enc <- s.parameters.get.map(_.get("client_encoding"))
        _   <- putStrLn(s"Logged in! Transaction status is $st and client_encoding is $enc")
        _   <- s.listen("foo", 10).to(anyLinesStdOut).compile.drain.start
        rs  <- s.quick(sql"select name, code, indepyear, population from country limit 20".query(country))
        _   <- rs.traverse(putStrLn)
        _   <- s.quick(sql"set seed = 0.123".command)
        _   <- s.quick(sql"set client_encoding = 'ISO8859_1'".command)
        _   <- s.quick(sql"set client_encoding = 'UTF8'".command)
        _   <- s.notify("foo", "here is a message")
        _   <- s.quick(sql"select current_user".query(name))
        _   <- s.prepare(sql"delete from country where population < $int2".command)
        ps  <- s.prepare(q)
        f1  <- s.stream(ps, 100000, 10).take(25).to(anyLinesStdOut).compile.drain.start
        f2  <- s.stream(ps, 10000, 5).to(anyLinesStdOut).compile.drain.start
        _   <- f1.join
        _   <- f2.join
        _   <- putStrLn("Done.")
      } yield ExitCode.Success
    }

}
