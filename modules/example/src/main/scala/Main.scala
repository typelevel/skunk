package example

import skunk._, skunk.implicits._

import cats.effect._
// import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.Sink.showLinesStdOut
// import scala.concurrent.duration._

object Main extends IOApp {
  import Codec._

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  def anyLinesStdOut[F[_]: Sync]: Sink[F, Any] =
    _.map(_.toString).to(showLinesStdOut)

  case class Country(name: String, code: String, indepyear: Option[Short], population: Int)
  val country: Codec[Country] =
    (varchar, bpchar, int2.opt, int4).imapN(Country.apply)(Country.unapply(_).get)

  // val frag = sql"population < $int4"

  val q: Query[Int ~ String, Country] = {
    val table = "country"
    sql"""
      SELECT name, code, indepyear, population
      FROM   #$table
      WHERE  population < $int4
      AND    code LIKE $bpchar
      -- and a comment at the end
    """.query(country)
  }

  def clientEncodingChanged(enc: String): IO[Unit] =
    putStrLn(s">>>> CLIENT ENCODING IS NOW: $enc")

  def hmm[F[_]: ConcurrentEffect](s: SessionPlus[F])(ps: s.PreparedQuery[Int ~ String, _]): F[Unit] =
    (s.stream(ps, 100000 ~ "%", 4).take(25) either s.stream(ps, 10000 ~ "%", 5))
      .to(anyLinesStdOut)
      .compile
      .drain

  def run(args: List[String]): IO[ExitCode] =
    SessionPlus[IO]("localhost", 5432).use { s =>
      for {
        _   <- s.startup("postgres", "world")
        _   <- s.parameter("client_encoding").evalMap(clientEncodingChanged).compile.drain.start
        st  <- s.transactionStatus.get
        enc <- s.parameters.get.map(_.get("client_encoding"))
        _   <- putStrLn(s"Logged in! Transaction status is $st and client_encoding is $enc")
        f   <- s.listen(id"foo", 10).to(anyLinesStdOut).compile.drain.start
        rs  <- s.quick(sql"select name, code, indepyear, population from country limit 20".query(country))
        _   <- rs.traverse(putStrLn)
        _   <- s.quick(sql"set seed = 0.123".command)
        _   <- s.quick(sql"set client_encoding = ISO8859_1".command)
        _   <- s.quick(sql"set client_encoding = UTF8".command)
        _   <- s.notify(id"foo", "here is a message")
        _   <- s.quick(sql"select current_user".query(name))
        _   <- s.prepare(q).use(hmm(s))
        _   <- f.cancel // we do this instead of joining since it will never finish
        _   <- putStrLn("Done.")
      } yield ExitCode.Success
    }

}
