package example

import cats.data.Kleisli
import cats._
import cats.data._
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
import scala.util.control.NonFatal
import natchez.honeycomb.Honeycomb

object Http4sExample extends IOApp {

  // A DATA MODEL WITH CIRCE JSON ENCODER

  case class Country(code: String, name: String)
  object Country {
    implicit val encoderCountry: CEncoder[Country] = deriveEncoder
  }

  // A SERVICE

  trait Countries[F[_]] {
    def byCode(code: String): F[Option[Country]]
    def all: Stream[F, Country]
  }

  object Countries {

    def fromSession[F[_]: Bracket[?[_], Throwable]: Trace](sess: Session[F]): Countries[F] =
      new Countries[F] {

        def countryQuery[A](where: Fragment[A]): Query[A, Country] =
          sql"SELECT code, name FROM country $where".query((bpchar(3) ~ varchar).gmap[Country])

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

  // GENERIC TRACING MIDDLEWARE, TO BE FACTORED OUT

  def natchezMiddleware[F[_]: Bracket[?[_], Throwable]: Trace](routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli { req =>

      val addRequestFields: F[Unit] =
        Trace[F].put(
          Tags.http.method(req.method.name),
          Tags.http.url(req.uri.renderString)
        )

      def addResponseFields(res: Response[F]): F[Unit] =
        Trace[F].put(
          Tags.http.status_code(res.status.code.toString)
        )

      def addErrorFields(e: Throwable): F[Unit] =
        Trace[F].put(
          Tags.error(true),
          "error.message" -> e.getMessage,
          "error.stacktrace" -> e.getStackTrace.mkString("\n"),
        )

      OptionT {
        routes(req).onError {
          case NonFatal(e)   => OptionT.liftF(addRequestFields *> addErrorFields(e))
        } .value.flatMap {
          case Some(handler) => addRequestFields *> addResponseFields(handler).as(handler.some)
          case None          => Option.empty[Response[F]].pure[F]
        }
      }
    }

  // ROUTES DELEGATING TO THE SERVICE, WITH TRACING

  def countryRoutes[F[_]: Concurrent: ContextShift: Defer: Trace]: HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._

    val pool: Resource[F, Countries[F]] =
      Session.single[F](
        host     = "localhost",
        user     = "jimmy",
        database = "world",
        password = Some("banana"),
      ).map(Countries.fromSession(_))

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

  // Normal constructor for an HttpApp in F *without* a Trace constraint.
  def app[F[_]: Concurrent: ContextShift](ep: EntryPoint[F]): Kleisli[F, Request[F], Response[F]] = {
    Router("/" -> ep.liftT(natchezMiddleware(countryRoutes))).orNotFound // <-- Lifted routes
  }

  // Normal server resource
  def server[F[_]: ConcurrentEffect: Timer](routes: HttpApp[F]): Resource[F, Server[F]] =
    BlazeServerBuilder[F]
      .bindHttp(8080, "localhost")
      .withHttpApp(routes)
      .resource

  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
    Honeycomb.entryPoint[F]("skunk-http4s-honeycomb") { ob =>
      Sync[F].delay {
        ob.setWriteKey("<api key>")
          .setDataset("Test")
          .build
      }
    }

  def runR[F[_]: ConcurrentEffect: ContextShift: Timer]: Resource[F, Unit] =
    for {
      ep <- entryPoint[F]
      _  <- server(app(ep))
    } yield ()

  // Main method instantiates F to IO
  def run(args: List[String]): IO[ExitCode] =
    runR[IO].use(_ => IO.never)

}

