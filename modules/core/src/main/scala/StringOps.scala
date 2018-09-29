package skunk

import cats.data.Chain
import cats.implicits._
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

// H   is like Encoder[A] :: Encoder[B] :: ... :: HNil
// Out is like A :: B :: ... :: HNil
trait Shuffle[H <: HList] {
  type Out <: HList
  def apply(h: H): (List[List[Type]], Encoder[Out])
}
object Shuffle {

  type Aux[H <: HList, O <: HList] = Shuffle[H] { type Out = O }

  implicit val hnil: Aux[HNil, HNil] =
    new Shuffle[HNil] {
      type Out = HNil
      def apply(h: HNil) = (
        Nil,
        new Encoder[HNil] {
          def encode(a: HNil) = Nil
          def oids = Nil
        }
      )
    }

  implicit def hcons[H, T <: HList](
    implicit ev: Shuffle[T]
  ): Aux[Encoder[H] :: T, H :: ev.Out] =
    new Shuffle[Encoder[H] :: T] {
      type Out = H :: ev.Out
      def apply(h: Encoder[H] :: T) = {
        val (ts, et) = ev(h.tail)
        (h.head.oids.toList :: ts, (h.head, et).contramapN(hl => (hl.head, hl.tail)))
      }
    }

  implicit def hcons2[H, T <: HList](
    implicit ev: Shuffle[T]
  ): Aux[Codec[H] :: T, H :: ev.Out] =
    new Shuffle[Codec[H] :: T] {
      type Out = H :: ev.Out
      def apply(h: Codec[H] :: T) = {
        val (ts, et) = ev(h.tail)
        (h.head.oids.toList :: ts, (h.head, et).contramapN(hl => (hl.head, hl.tail)))
      }
    }

}
