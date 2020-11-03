// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package natchez.http4s

import cats.~>
import cats.data.{ Kleisli, OptionT }
import cats.effect.Bracket
import cats.syntax.all._
import natchez.{ EntryPoint, Kernel, Span }
import org.http4s.HttpRoutes
import natchez.Trace
import natchez.Tags
import scala.util.control.NonFatal
import org.http4s.Response
import cats.effect.Resource
import cats.Defer
import natchez.TraceValue
import cats.Monad

object implicits {

  // Given an entry point and HTTP Routes in Kleisli[F, Span[F], *] return routes in F. A new span
  // is created with the URI path as the name, either as a continuation of the incoming trace, if
  // any, or as a new root. This can likely be simplified, I just did what the types were saying
  // and it works so :shrug:
  private def liftT[F[_]: Bracket[*[_], Throwable]](
    entryPoint: EntryPoint[F])(
    routes:     HttpRoutes[Kleisli[F, Span[F], *]]
  ): HttpRoutes[F] =
    Kleisli { req =>
      type G[A]  = Kleisli[F, Span[F], A]
      val lift   = λ[F ~> G](fa => Kleisli(_ => fa))
      val kernel = Kernel(req.headers.toList.map(h => (h.name.value -> h.value)).toMap)
      val spanR  = entryPoint.continueOrElseRoot(req.uri.path, kernel)
      OptionT {
        spanR.use { span =>
          val lower = λ[G ~> F](_(span))
          routes.run(req.mapK(lift)).mapK(lower).map(_.mapK(lower)).value
        }
      }
    }

  implicit class EntryPointOps[F[_]](self: EntryPoint[F]) {

    private def dummySpan(
      implicit ev: Monad[F]
    ): Span[F] =
      new Span[F] {
        val kernel: F[Kernel] = Kernel(Map.empty).pure[F]
        def put(fields: (String, TraceValue)*): F[Unit] = Monad[F].unit
        def span(name: String): Resource[F, Span[F]] = Monad[Resource[F, *]].pure(this)
      }

    def liftT(routes: HttpRoutes[Kleisli[F, Span[F], *]])(
      implicit ev: Bracket[F, Throwable]
    ): HttpRoutes[F] =
      implicits.liftT(self)(routes)

    /**
     * Lift an `HttpRoutes`-yielding resource that consumes `Span`s into the bare effect. We do this
     * by ignoring any tracing that happens during allocation and freeing of the `HttpRoutes`
     * resource. The reasoning is that such a resource typically lives for the lifetime of the
     * application and it's of little use to keep a span open that long.
     */
    def liftR(routes: Resource[Kleisli[F, Span[F], *], HttpRoutes[Kleisli[F, Span[F], *]]])(
      implicit ev: Bracket[F, Throwable],
                d: Defer[F]
    ): Resource[F, HttpRoutes[F]] =
      routes.map(liftT).mapK(λ[Kleisli[F, Span[F], *] ~> F] { fa =>
        fa.run(dummySpan)
      })

  }

  /**
   * A middleware that adds the following standard fields to the current span:
   *
   * - "http.method"      -> "GET", "PUT", etc.
   * - "http.url"         -> request URI (not URL)
   * - "http.status_code" -> "200", "403", etc. // why is this a string?
   * - "error"            -> true // not present if no error
   *
   * In addition the following non-standard fields are added in case of error:
   *
   * - "error.message"    -> Exception message
   * - "error.stacktrace" -> Exception stack trace as a multi-line string
   */
  def natchezMiddleware[F[_]: Bracket[*[_], Throwable]: Trace](routes: HttpRoutes[F]): HttpRoutes[F] =
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
          "error.message"    -> e.getMessage,
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

}
