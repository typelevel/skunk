package natchez.http4s

import cats.~>
import cats.data.{ Kleisli, OptionT }
import cats.effect.Bracket
import natchez.{ EntryPoint, Kernel, Span }
import org.http4s.HttpRoutes

object implicits {

  // Given an entry point and HTTP Routes in Kleisli[F, Span[F], ?] return routes in F. A new span
  // is created with the URI path as the name, either as a continuation of the incoming trace, if
  // any, or as a new root. This can likely be simplified, I just did what the types were saying
  // and it works so :shrug:
  private def liftT[F[_]: Bracket[?[_], Throwable]](
    entryPoint: EntryPoint[F])(
    routes:     HttpRoutes[Kleisli[F, Span[F], ?]]
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
    def liftT(routes: HttpRoutes[Kleisli[F, Span[F], ?]])(
      implicit ev: Bracket[F, Throwable]
    ): HttpRoutes[F] =
      implicits.liftT(self)(routes)
  }

}
