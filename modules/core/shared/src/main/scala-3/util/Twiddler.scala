// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package util

import scala.annotation.implicitNotFound
import scala.deriving.Mirror

import skunk.implicits._

/** Witness that type `A` is isomorphic to a twiddle list. */
@implicitNotFound("Cannot construct a mapping between the source (which must be a twiddle-list type) and the specified target type ${A} (which must be a case class of the same structure).")
trait Twiddler[A] {
  type Out
  def to(h: A): Out
  def from(o: Out): A
}

@deprecated("Use twiddle tuples (*:) instead", "0.6")
object Twiddler {

  def apply[H](using ev: Twiddler[H]): ev.type = ev

  type Aux[A, O] = Twiddler[A] { type Out = O }

  given product1[P <: Product, A](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = A *: EmptyTuple }
    ): (Twiddler[P] { type Out = A }) =
      new Twiddler[P] {
        type Out = A
        def to(p: P): Out = Tuple.fromProductTyped(p) match { case a *: EmptyTuple => a }
        def from(o: Out): P = o match { case a => m.fromProduct(a *: EmptyTuple) }
      }

  given product2[P <: Product, A, B](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B) }
    ): (Twiddler[P] { type Out = A ~ B }) =
      new Twiddler[P] {
        type Out = A ~ B
        def to(p: P): Out = Tuple.fromProductTyped(p) match { case (a, b) => a ~ b }
        def from(o: Out): P = o match { case a ~ b => m.fromProduct((a, b)) }
      }

  given product3[P <: Product, A, B, C](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C) }
    ): (Twiddler[P] { type Out = A ~ B ~ C }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C
        def to(p: P): Out = Tuple.fromProductTyped(p) match { case (a, b, c) => a ~ b ~ c }
        def from(o: Out): P = o match { case a ~ b ~ c => m.fromProduct((a, b, c)) }
      }

  given product4[P <: Product, A, B, C, D](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D
        def to(p: P): Out = Tuple.fromProductTyped(p) match { case (a, b, c, d) => a ~ b ~ c ~ d }
        def from(o: Out): P = o match { case a ~ b ~ c ~ d => m.fromProduct((a, b, c, d)) }
      }

  given product5[P <: Product, A, B, C, D, E](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E
        def to(p: P): Out = Tuple.fromProductTyped(p) match { case (a, b, c, d, e) => a ~ b ~ c ~ d ~ e }
        def from(o: Out): P = o match { case a ~ b ~ c ~ d ~ e => m.fromProduct((a, b, c, d, e)) }
      }

  given product6[P <: Product, A, B, C, D, E, F](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F
        def to(p: P): Out = Tuple.fromProductTyped(p) match { case (a, b, c, d, e, f) => a ~ b ~ c ~ d ~ e ~ f }
        def from(o: Out): P = o match { case a ~ b ~ c ~ d ~ e ~ f => m.fromProduct((a, b, c, d, e, f)) }
      }

  given product7[P <: Product, A, B, C, D, E, F, G](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g) => a ~ b ~ c ~ d ~ e ~ f ~ g
          }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g => m.fromProduct((a, b, c, d, e, f, g))
        }
      }

  given product8[P <: Product, A, B, C, D, E, F, G, H](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h => m.fromProduct((a, b, c, d, e, f, g, h))
        }
      }

  given product9[P <: Product, A, B, C, D, E, F, G, H, I](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i => m.fromProduct((a, b, c, d, e, f, g, h, i))
        }
      }

  given product10[P <: Product, A, B, C, D, E, F, G, H, I, J](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j => m.fromProduct((a, b, c, d, e, f, g, h, i, j))
        }
      }

  given product11[P <: Product, A, B, C, D, E, F, G, H, I, J, K](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J, K) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j, k) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k => m.fromProduct((a, b, c, d, e, f, g, h, i, j, k))
        }
      }

  given product12[P <: Product, A, B, C, D, E, F, G, H, I, J, K, L](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J, K, L) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l => m.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l))
        }
      }

  //type names M and P are in use so we skip over to Q
  given product13[P <: Product, A, B, C, D, E, F, G, H, I, J, K, L, Q](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J, K, L, Q) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, q) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q =>
            m.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, q))
        }
      }

  given product14[P <: Product, A, B, C, D, E, F, G, H, I, J, K, L, Q, R](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J, K, L, Q, R) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q ~ R }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q ~ R
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, q, r) => a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q ~ r
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q ~ r =>
            m.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, q, r))
        }
      }

  given product15[P <: Product, A, B, C, D, E, F, G, H, I, J, K, L, Q, R, S](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J, K, L, Q, R, S) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q ~ R ~ S }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q ~ R ~ S
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, q, r, s) =>
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q ~ r ~ s
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q ~ r ~ s =>
            m.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, q, r, s))
        }
      }

  given product16[P <: Product, A, B, C, D, E, F, G, H, I, J, K, L, Q, R, S, T](
    using m: Mirror.ProductOf[P] { type MirroredElemTypes = (A, B, C, D, E, F, G, H, I, J, K, L, Q, R, S, T) }
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q ~ R ~ S ~ T }) =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ Q ~ R ~ S ~ T
        def to(p: P): Out = Tuple.fromProductTyped(p) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, q, r, s, t) =>
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q ~ r ~ s ~ t
        }
        def from(o: Out): P = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ q ~ r ~ s ~ t =>
            m.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, q, r, s, t))
        }
      }

  // For binary compatibility with Skunk 0.3.1 and prior
  private[skunk] def product1[P <: Product, A](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= A *: EmptyTuple
    ): (Twiddler[P] { type Out = A }) =
      product1(using m.asInstanceOf)

  private[skunk] def product2[P <: Product, A, B](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= (A, B)
    ): (Twiddler[P] { type Out = A ~ B  }) =
      product2(using m.asInstanceOf)

  private[skunk] def product3[P <: Product, A, B, C](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= (A, B, C)
    ): (Twiddler[P] { type Out = A ~ B ~ C }) =
      product3(using m.asInstanceOf)

  private[skunk] def product4[P <: Product, A, B, C, D](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= (A, B, C, D)
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D }) =
      product4(using m.asInstanceOf)

  private[skunk] def product5[P <: Product, A, B, C, D, E](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= (A, B, C, D, E)
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E }) =
      product5(using m.asInstanceOf)

  private[skunk] def product6[P <: Product, A, B, C, D, E, F](
    using m: Mirror.ProductOf[P],
          i: m.MirroredElemTypes =:= (A, B, C, D, E, F)
    ): (Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F }) =
      product6(using m.asInstanceOf)
}

