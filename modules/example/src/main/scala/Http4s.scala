// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.Defer
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import io.jaegertracing.Configuration.ReporterConfiguration
import io.jaegertracing.Configuration.SamplerConfiguration
import natchez.{ EntryPoint, Trace }
import natchez.http4s.implicits._ // TODO: move this to Natchez!
import natchez.jaeger.Jaeger
import org.http4s.{ HttpApp, HttpRoutes }
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.{ Server, Router }
import org.http4s.server.blaze.BlazeServerBuilder
import skunk.{ Session, Fragment, Query, Void }
import skunk.codec.text.{ varchar, bpchar }
import skunk.implicits._

/*

A small but complete web service that serves data from the `world` database and accumulates
request traces to a tracing back-end. Note that the effect `F` is abstract throughout. To run
this example you need the Skunk sample database (as with all examples) and also need a Jaeger
trace collector running. You can stand this up via:

 docker run -d --name jaeger \
    -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 \
    -p 5775:5775/udp \
    -p 6831:6831/udp \
    -p 6832:6832/udp \
    -p 5778:5778 \
    -p 16686:16686 \
    -p 14268:14268 \
    -p 9411:9411 \
    jaegertracing/all-in-one:1.8

Then run this program

  bloop run example --main example.Http4sExample

And try some requests

  curl -i http://localhost:8080/country/USA
  curl -i http://localhost:8080/country/foobar

And Then go to http://localhost:16686/search and select the `skunk-http4s-example` service and
click search to see request traces.

*/
object Http4sExample extends IOApp {

  // A data model with a Circe `Encoder`
  case class Country(code: String, name: String)
  object Country {
    implicit val encoderCountry: Encoder[Country] = deriveEncoder
  }

  // A service interface and companion factory method.
  trait Countries[F[_]] {
    def byCode(code: String): F[Option[Country]]
    def all: Stream[F, Country]
  }
  object Countries {

    // An implementation of `Countries`, given a `Session`.
    def fromSession[F[_]: Bracket[?[_], Throwable]: Trace](
      sess: Session[F]
    ): Countries[F] =
      new Countries[F] {

        def countryQuery[A](where: Fragment[A]): Query[A, Country] =
          sql"SELECT code, name FROM country $where".query((bpchar(3) ~ varchar).gmap[Country])

        def all: Stream[F,Country] =
          Stream.resource(sess.prepare(countryQuery(Fragment.empty)))
                .flatMap(_.stream(Void, 64))

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
        Ok(Stream.resource(pool).flatMap(_.all.map(_.asJson)))

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
  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    Jaeger.entryPoint[F]("skunk-http4s-example") { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
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
