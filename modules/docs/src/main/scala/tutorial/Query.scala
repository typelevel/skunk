package tutorial

object Queries1 {

  //#query-a
  import cats.effect.IO
  import skunk._
  import skunk.implicits._
  import skunk.codec.all._

  val a: Query[Void, String] =
    sql"SELECT name FROM country".query(varchar)
  //#query-a

  //#query-a-exec
  // assuming we have a session
  def sess: Session[IO] = ???

  sess.execute(a) // IO[List[String]]
  //#query-a-exec

  //#query-b
  val b: Query[Void, String ~ Int] =
    sql"SELECT name, population FROM country".query(varchar ~ int4)
  //#query-b

  //#query-c
  case class Country(name: String, population: Int)

  val c: Query[Void, Country] =
    sql"SELECT name, population FROM country"
      .query(varchar ~ int4)
      .map { case n ~ p => Country(n, p) }
  //#query-c

  //#query-d
  val country: Decoder[Country] =
    (varchar ~ int4).map { case (n, p) => Country(n, p) }

  val d: Query[Void, Country] =
    sql"SELECT name, population FROM country".query(country)
  //#query-d


}
