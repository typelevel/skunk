package skunk.dsl

import cats.data.Chain
import shapeless._

class StringOps(sc: StringContext) {

  object sql extends ProductArgs {
    def applyProduct[H <: HList](h: H)(
      implicit ev: Shuffle[H]
    ): Fragment[ev.Out] = {
      val (ts, e) = ev(h)
      Fragment(Chain(sc.parts: _*), ts, e)
    }
  }

}

trait ToStringOps {
  implicit def toStringOps(sc: StringContext): StringOps =
    new StringOps(sc)
}

