package example

import skunk._
import skunk.codec.all._
import skunk.implicits._
import cats.effect._
import cats.implicits._
import fs2._
import fs2.Sink.showLinesStdOut

object Main extends IOApp {
  import Codec._

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  def anyLinesStdOut[F[_]: Sync]: Sink[F, Any] =
    _.map(_.toString).to(showLinesStdOut)

  val country: Codec[Country] =
    (varchar, bpchar, int2.opt, int4).imapN(Country.apply)(Country.unapply(_).get)

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

  def hmm[F[_]: ConcurrentEffect](s: Session[F])(ps: s.PreparedQuery[Int ~ String, _]): F[Unit] =
    (s.stream(ps, 4, 100000 ~ "%").take(25) either s.stream(ps, 4, 10000 ~ "%"))
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
          f2  <- s.listen(id"foo", 10).to(anyLinesStdOut).compile.drain.start
          rs  <- s.quick(sql"select name, code, indepyear, population from country limit 20".query(country))
          _   <- rs.traverse(putStrLn)
          _   <- s.quick(sql"set seed = 0.123".command)
          _   <- s.quick(sql"set client_encoding = ISO8859_1".command)
          _   <- s.notify(id"foo", "here is a message")
          _   <- s.quick(sql"select current_user".query(name))
          _   <- s.prepare(q).use(hmm(s))
          _   <- s.prepare(in(3)).use { s.stream(_, 100, List("FRA", "USA", "GAB")).to(anyLinesStdOut).compile.drain }
          _   <- f2.cancel // otherwise it will run forever
          _   <- f1.cancel // otherwise it will run forever
          _   <- s.quick(sql"select 'x'::char(10)".query(varchar))
          _   <- s.prepare(sql"select 1".query(int4)).use { ps => s.stream(ps, 10).to(anyLinesStdOut).compile.drain }
          _   <- putStrLn("Done.")
        } yield ExitCode.Success
      } *>
      putStrLn("------------------------- STARTING SECOND SESSION --------") *>
      p.use { s =>
        for {
          _   <- s.quick(sql"set seed = 0.123".command)
          _   <- s.quick(sql"set client_encoding = ISO8859_1".command)
        } yield ExitCode.Success
      }
    }

}


case class Country(name: String, code: String, indepyear: Option[Short], population: Int)



class CountryOps[F[_]: Sync](s: Session[F]) {

  import Codec._
  import Codecs._

  def lookupByCode(code: String): F[Option[Country]] =
    s.prepare(Statements.lookupByCode).use { s.option(_, code) }

  object Codecs {

    val country: Codec[Country] =
      (varchar, bpchar, int2.opt, int4).imapN(Country.apply)(Country.unapply(_).get)

  }

  object Statements {

    def lookupByCode: Query[String, Country] =
      sql"""
        SELECT name, code, indepyear, population
        FROM   country
        WHERE  code = $bpchar
      """.query(country)

  }

}

trait CountryOps2[F[_]] {
  def lookupByCode(code: String): F[Option[Country]]
}

object CountryOps2 {
  import Codec._
  import Codecs._

  def apply[F[_]: Bracket[?[_], Throwable]](s: Session[F]): Resource[F, CountryOps2[F]] =
    for {
      ps1 <- s.prepare(Statements.lookupByCode)
    } yield
      new CountryOps2[F] {
        def lookupByCode(code: String): F[Option[Country]] =
          s.option(ps1, code)
      }

   object Codecs {

    val country: Codec[Country] =
      (varchar, bpchar, int2.opt, int4).imapN(Country.apply)(Country.unapply(_).get)

  }

  object Statements {

    def lookupByCode: Query[String, Country] =
      sql"""
        SELECT name, code, indepyear, population
        FROM   country
        WHERE  code = $bpchar
      """.query(country)

  }
}