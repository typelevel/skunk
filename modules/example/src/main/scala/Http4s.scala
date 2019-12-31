package example

import cats._
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.{ Encoder => CEncoder }
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import natchez._
import natchez.http4s.implicits._
import org.http4s.{ Query => _, _ }
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server._
import org.http4s.server.blaze.BlazeServerBuilder
import skunk._, skunk.implicits._, skunk.codec.all._
import natchez.honeycomb.Honeycomb

object Http4sExample extends IOApp {

  // A data model
  case class Country(code: String, name: String)
  object Country {
    implicit val encoderCountry: CEncoder[Country] = deriveEncoder
  }

  // A service interface
  trait Countries[F[_]] {
    def byCode(code: String): F[Option[Country]]
    def all: Stream[F, Country]
  }

  // A companion object for our service, with a constructor.
  object Countries {

    // An implementation of `Countries`, given a Skunk `Session`.
    def fromSession[F[_]: Bracket[?[_], Throwable]: Trace](
      sess: Session[F]
    ): Countries[F] =
      new Countries[F] {

        def countryQuery[A](where: Fragment[A]): Query[A, Country] =
          sql"SELECT code, name FROM country $where".query((bpchar(3) ~ varchar).gmap[Country])

        // The trace indicates that the session is returned to the pool before we're done using it!
        // What's up with that?

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

  // Resource yielding a service pool.
  def pool[F[_]: Concurrent: ContextShift: Trace]: Resource[F, Resource[F, Countries[F]]] =
    Session.pooled[F](
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      max      = 10,
    ).map(_.map(Countries.fromSession(_)))

  // Routes delegating to `Countries` instances, taken from a pool.
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

  // Resource yielding a `Countries` pool becomes a resource yielding `HttpRoutes`.
  def routes[F[_]: Concurrent: ContextShift: Trace]: Resource[F, HttpRoutes[F]] =
    pool.map(p => natchezMiddleware(countryRoutes(p)))

  // Given an `EntryPoint` we can discharge the `Trace` constraint on `routes`. We need to do this
  // because `ConcurrentEffect` (required by Blaze) and `Trace` cannot be satisfied together. This
  // is the important trick. In the call to `routes` the effect `F` will be instantiated as
  // `Kleisli[F, Span[F], ?]` but we never have to know that.
  def routesʹ[F[_]: Concurrent: ContextShift](
    ep: EntryPoint[F]
  ): Resource[F, HttpRoutes[F]] =
    ep.liftR(routes)

  // Resource yielding our Natchez `EntryPoint`.
  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
    Honeycomb.entryPoint[F]("skunk-http4s-honeycomb") { ob =>
      Sync[F].delay {
        ob.setWriteKey("<api key>")
          .setDataset("Test")
          .build
      }
    }

  // Resource yielding a `Server` … nothing special here.
  def server[F[_]: ConcurrentEffect: Timer](routes: HttpApp[F]): Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .bindHttp(8080, "localhost")
      .withHttpApp(routes)
      .resource

  // Our application as a resource.
  def runR[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Unit] =
    for {
      ep <- entryPoint[F]
      rs <- routesʹ(ep)
      _  <- server(Router("/" -> rs).orNotFound)
    } yield ()

  // Main method instantiates F to IO
  def run(args: List[String]): IO[ExitCode] =
    runR[IO].use(_ => IO.never)

}

