package example

import cats.Defer
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import natchez.{ EntryPoint, Tags, Trace }
import natchez.http4s.implicits._
import org.http4s.{ HttpApp, HttpRoutes }
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.{ Server, Router }
import org.http4s.server.blaze.BlazeServerBuilder
import skunk.{ Session, Fragment, Query, Void }
import skunk.implicits._
import skunk.codec.text.{ varchar, bpchar }
import natchez.honeycomb.Honeycomb

object Http4sExample extends IOApp {

  // A data model with a Circe `Encoder`
  case class Country(code: String, name: String)
  object Country {
    implicit val encoderCountry: Encoder[Country] = deriveEncoder
  }

  // A service interface
  trait Countries[F[_]] {
    def byCode(code: String): F[Option[Country]]
    def all: Stream[F, Country]
  }
  object Countries {

    // An implementation of `Countries`, given a Skunk `Session`.
    def fromSession[F[_]: Bracket[?[_], Throwable]: Trace](
      sess: Session[F]
    ): Countries[F] =
      new Countries[F] {

        // Query for countries, with pluggable WHERE clause.
        def countryQuery[A](where: Fragment[A]): Query[A, Country] =
          sql"SELECT code, name FROM country $where".query((bpchar(3) ~ varchar).gmap[Country])

        // Tracing for streaming actions isn't great yet. The trace indicates that the session is
        // returned to the pool before we're done using it! What's up with that?
        def all: Stream[F,Country] =
          for {
            _  <- Stream.eval(Trace[F].put(Tags.component("countries")))
            pq <- Stream.resource(sess.prepare(countryQuery(Fragment.empty)))
            c  <- pq.stream(Void, 64)
          } yield c

        def byCode(code: String): F[Option[Country]] =
          Trace[F].span(s"""Country.byCode("$code")""") {
            sess.prepare(countryQuery(sql"WHERE code = ${bpchar(3)}"))
                .use(_.option(code))
          }

      }
  }

  // `HttpRoutes` delegating to `Countries` instances, taken from a pool.
  def countryRoutes[F[_]: Concurrent: ContextShift: Defer: Trace](
    pool: Resource[F, Countries[F]]
  ): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._
    HttpRoutes.of[F] {

      case GET -> Root / "country" / code =>
        pool.use { countries =>
          countries.byCode(code).flatMap {
            case Some(c) => Ok(c.asJson)
            case None    => NotFound(s"No country has code $code.")
          }
        }

      case GET -> Root / "countries" =>
        pool.use { countries =>
          Ok(countries.all.map(_.asJson))
        }

    }
  }

  // Resource yielding a `Countries` pool.
  def pool[F[_]: Concurrent: ContextShift: Trace]: Resource[F, Resource[F, Countries[F]]] =
    Session.pooled[F](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      max      = 10,
    ).map(_.map(Countries.fromSession(_)))

  // Resource yielding `HttpRoutes`.
  def routes[F[_]: Concurrent: ContextShift: Trace]: Resource[F, HttpRoutes[F]] =
    pool.map(p => natchezMiddleware(countryRoutes(p)))

  // Resource yielding our Natchez tracing `EntryPoint`.
  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
    Honeycomb.entryPoint[F]("skunk-http4s-honeycomb") { ob =>
      Sync[F].delay {
        ob.setWriteKey("<api key>")
          .setDataset("Test")
          .build
      }
    }

  // Resource yielding a running `Server` for a given `HttpApp`.
  def server[F[_]: ConcurrentEffect: Timer](
    app: HttpApp[F]
  ): Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .bindHttp(8080, "localhost")
      .withHttpApp(app)
      .resource

  // Our application as a resource.
  def runR[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Unit] =
    for {
      ep <- entryPoint[F]
      rs <- ep.liftR(routes) // discharge the `Trace` constraint for `routes`
      _  <- server(Router("/" -> rs).orNotFound)
    } yield ()

  // Main method instantiates F to IO
  def run(args: List[String]): IO[ExitCode] =
    runR[IO].use(_ => IO.never)

}
