package example

import skunk._
import skunk.codec.all._
import skunk.implicits._
import cats.effect._
import cats.implicits._
import fs2._
import fs2.Sink.showLinesStdOut

// This does a lot of stuff and is mostly just to test features as they're being added. This class
// will probably go away.
object Main extends IOApp {
  import Codec._

  case class Country(name: String, code: String, indepyear: Option[Short], population: Int)

  val country: Codec[Country] =
    (varchar, bpchar, int2.opt, int4).imapN(Country.apply)(Country.unapply(_).get)

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  def anyLinesStdOut[F[_]: Sync]: Sink[F, Any] =
    _.map(_.toString).to(showLinesStdOut)

  val fra0 = sql"true"

  val frag = sql"population < $int4 AND $fra0"

  val q: Query[Int ~ String, Country] = {
    val table = "country"
    sql"""
      SELECT name, code, indepyear, population
      FROM   #$table -- literal interpolation
      WHERE  $frag   -- nested fragment
      AND    code LIKE $bpchar
      -- and a comment at the end
    """.query(country)
  }

  def in(ncodes: Int): Query[List[String], Country] =
    sql"""
      SELECT name, code, indepyear, population
      FROM   country
      WHERE  code in (${bpchar.list(ncodes)})
    """.query(country)

  def clientEncodingChanged(enc: String): IO[Unit] =
    putStrLn(s">>>> CLIENT ENCODING IS NOW: $enc")

  def hmm[F[_]: ConcurrentEffect](ps: PreparedQuery[F, Int ~ String, _]): F[Unit] =
    (ps.stream(100000 ~ "%", 4).take(25) either ps.stream(10000 ~ "%", 4))
      .to(anyLinesStdOut)
      .compile
      .drain

  val pool: SessionPool[IO] =
    Session.pooled(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
      max      = 10,
    )

  def run(args: List[String]): IO[ExitCode] =
    pool.use { p =>
      p.use { s =>
        for {
          f1  <- s.parameter("client_encoding").evalMap(clientEncodingChanged).compile.drain.start
          st  <- s.transactionStatus.get
          enc <- s.parameters.get.map(_.get("client_encoding"))
          _   <- putStrLn(s"Logged in! Transaction status is $st and client_encoding is $enc")
          f2  <- s.channel(id"foo").listen(10).to(anyLinesStdOut).compile.drain.start
          rs  <- s.execute(sql"select name, code, indepyear, population from country limit 20".query(country))
          _   <- rs.traverse(putStrLn)
          _   <- s.execute(sql"set seed = 0.123".command)
          _   <- s.execute(sql"set client_encoding = ISO8859_1".command)
          _   <- s.channel(id"foo").notify("here is a message")
          _   <- s.execute(sql"select current_user".query(name))
          _   <- s.prepare(q).use(hmm(_))
          _   <- s.prepare(in(3)).use { _.stream(List("FRA", "USA", "GAB"), 100).to(anyLinesStdOut).compile.drain }
          _   <- f2.cancel // otherwise it will run forever
          _   <- f1.cancel // otherwise it will run forever
          _   <- s.execute(sql"select 'x'::char(10)".query(varchar))
          _   <- s.prepare(sql"select 1".query(int4)).use { _.stream(Void, 10).to(anyLinesStdOut).compile.drain }
          _   <- putStrLn("Done.")
        } yield ExitCode.Success
      } *>
      putStrLn("------------------------- STARTING SECOND SESSION --------") *>
      p.use { s =>
        for {
          _   <- s.execute(sql"set seed = 0.123".command)
          _   <- s.execute(sql"set client_encoding = ISO8859_1".command)
        } yield ExitCode.Success
      }
    }

}


