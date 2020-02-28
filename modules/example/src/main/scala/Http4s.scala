// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats._
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
import fs2.io.tcp.SocketGroup
import skunk.util.Pool
import skunk.util.Recycler

/**
 * A small but complete web service that serves data from the `world` database and accumulates
 * request traces to a tracing back-end. Note that the effect `F` is abstract throughout. This
 * example uses the Jaeger trace collector that's provided as part of the project's docker-compose
 * file. So run this program and then try some requests:
 *
 *  curl -i http://localhost:8080/country/USA
 *  curl -i http://localhost:8080/country/foobar
 *
 * And Then go to http://localhost:16686/search and select the `skunk-http4s-example` service and
 * click search to see request traces. You will see that the first trace authenticates and prepares
 * statements, but subsequent requests (on the same session, which is likely the case if you're
 * monkey-testing) do not.
 */
object Http4sExample extends IOApp {

  /** A data model with a Circe `Encoder` */
  case class Country(code: String, name: String)
  object Country {
    implicit val encoderCountry: Encoder[Country] = deriveEncoder
  }

  /** A service interface and companion factory method. */
  trait Countries[F[_]] {
    def byCode(code: String): F[Option[Country]]
    def all: Stream[F, Country]
  }

  /**
   * A refinement that provides a handle to the underlying session. We will use this to implement
   * our pool recycler.
   */
  trait PooledCountries[F[_]] extends Countries[F] {
    def session: Session[F]
  }

  def pooledCountriesRecycler[F[_]: Monad]: Recycler[F, PooledCountries[F]] =
    Session.Recyclers.minimal[F].contramap(_.session)

  /** Given a `Session` we can create a `Countries` resource with pre-prepared statements. */
  def countriesFromSession[F[_]: Bracket[?[_], Throwable]: Trace](
    sess: Session[F]
  ): Resource[F, PooledCountries[F]] = {

    def countryQuery[A](where: Fragment[A]): Query[A, Country] =
      sql"SELECT code, name FROM country $where".query((bpchar(3) ~ varchar).gmap[Country])

    for {
      psAll    <- sess.prepare(countryQuery(Fragment.empty))
      psByCode <- sess.prepare(countryQuery(sql"WHERE code = ${bpchar(3)}"))
    } yield new PooledCountries[F] {

      def byCode(code: String): F[Option[Country]] =
        Trace[F].span(s"""Country.byCode("$code")""") {
          psByCode.option(code)
        }

      def all: Stream[F,Country] =
        psAll.stream(Void, 64)

      def session: Session[F] =
        sess

    }

  }

  /**
   * Given a `SocketGroup` we can construct a session resource, and from that construct a
   * `Countries` resource.
   */
  def countriesFromSocketGroup[F[_]: Concurrent: ContextShift: Trace](
    socketGroup: SocketGroup
  ): Resource[F, PooledCountries[F]] =
    Session.fromSocketGroup(
      host         = "localhost",
      user         = "jimmy",
      database     = "world",
      password     = Some("banana"),
      socketGroup = socketGroup
    ).flatMap(countriesFromSession(_))

  /** Resource yielding a pool of `Countries`, backed by a single `Blocker` and `SocketGroup`. */
  def pool[F[_]: Concurrent: ContextShift: Trace]: Resource[F, Resource[F, Countries[F]]] =
    for {
      b  <- Blocker[F]
      sg <- SocketGroup[F](b)
      pc  = countriesFromSocketGroup(sg)
      r  <- Pool.of(pc, 10)(pooledCountriesRecycler)
    } yield r.widen // forget we're a PooledCountries

  /** Given a pool of `Countries` we can create an `HttpRoutes`. */
  def countryRoutes[F[_]: Concurrent: ContextShift: Defer: Trace](
    pool: Resource[F, Countries[F]]
  ): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._
    HttpRoutes.of[F] {

      case GET -> Root / "country" / code =>
        pool.use { countries =>
          Trace[F].put("country" -> code) *> // add param to current span
          countries.byCode(code).flatMap {
            case Some(c) => Ok(c.asJson)
            case None    => NotFound(s"No country has code $code.")
          }
        }

      case GET -> Root / "countries" =>
        Ok(Stream.resource(pool).flatMap(_.all.map(_.asJson)))

    }
  }

  /**
   * Using `pool` above we can create `HttpRoutes` resource. We also add some standard tracing
   * middleware while we're at it.
   */
  def routes[F[_]: Concurrent: ContextShift: Trace]: Resource[F, HttpRoutes[F]] =
    pool.map(p => natchezMiddleware(countryRoutes(p)))

  /** Our Natchez `EntryPoint` resource. */
  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] = {
    Jaeger.entryPoint[F]("skunk-http4s-example") { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
  }

  /** Given an `HttpApp` we can create a running `Server` resource. */
  def server[F[_]: ConcurrentEffect: Timer](
    app: HttpApp[F]
  ): Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .bindHttp(8080, "localhost")
      .withHttpApp(app)
      .resource

  /** Our application as a resource. */
  def runR[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Unit] =
    for {
      ep <- entryPoint[F]
      rs <- ep.liftR(routes) // Discharge the `Trace` constraint for `routes`. Type argument here
                             // will be inferred as Kleisli[F, Span[F], ?] but we never see that
                             // type in our code, which makes it a little nicer.
      _  <- server(Router("/" -> rs).orNotFound)
    } yield ()

  /** Main method instantiates `F` to `IO` and `use`s our resource forever. */
  def run(args: List[String]): IO[ExitCode] =
    runR[IO].use(_ => IO.never)

}
