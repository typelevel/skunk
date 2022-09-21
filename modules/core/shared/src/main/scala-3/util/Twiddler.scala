// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package util

import scala.annotation.implicitNotFound
import scala.quoted._
import scala.deriving.Mirror

import skunk.implicits._

/** Witness that type `A` is isomorphic to a twiddle list. */
@implicitNotFound("Cannot construct a mapping between the source (which must be a twiddle-list type) and the specified target type ${A} (which must be a case class of the same structure).")
trait Twiddler[A] {
  type Out
  def to(h: A): Out
  def from(o: Out): A
}

object Twiddler {

  def apply[H](implicit ev: Twiddler[H]): ev.type = ev

  type Aux[A, O] = Twiddler[A] { type Out = O }

  implicit def product1[P <: Product, A](
    implicit m: Mirror.ProductOf[P],
             i: m.MirroredElemTypes =:= A *: EmptyTuple
    ): Twiddler[P] { type Out = A  } =
      new Twiddler[P] {
        type Out = A
        def to(p: P): Out = i(Tuple.fromProductTyped(p)) match { case a *: EmptyTuple => a }
        def from(o: Out): P = o match { case a => m.fromProduct(a *: EmptyTuple) }
      }

  implicit def product2[P <: Product, A, B](
    implicit m: Mirror.ProductOf[P],
             i: m.MirroredElemTypes =:= (A, B)
    ): Twiddler[P] { type Out = A ~ B  } =
      new Twiddler[P] {
        type Out = A ~ B
        def to(p: P): Out = i(Tuple.fromProductTyped(p)) match { case (a, b) => a ~ b }
        def from(o: Out): P = o match { case a ~ b => m.fromProduct((a, b)) }
      }

  implicit def product3[P <: Product, A, B, C](
    implicit m: Mirror.ProductOf[P],
             i: m.MirroredElemTypes =:= (A, B, C)
    ): Twiddler[P] { type Out = A ~ B ~ C } =
      new Twiddler[P] {
        type Out = A ~ B ~ C
        def to(p: P): Out = i(Tuple.fromProductTyped(p)) match { case (a, b, c) => a ~ b ~ c }
        def from(o: Out): P = o match { case a ~ b ~ c => m.fromProduct((a, b, c)) }
      }

  implicit def product4[P <: Product, A, B, C, D](
    implicit m: Mirror.ProductOf[P],
             i: m.MirroredElemTypes =:= (A, B, C, D)
    ): Twiddler[P] { type Out = A ~ B ~ C ~ D } =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D
        def to(p: P): Out = i(Tuple.fromProductTyped(p)) match { case (a, b, c, d) => a ~ b ~ c ~ d }
        def from(o: Out): P = o match { case a ~ b ~ c ~ d => m.fromProduct((a, b, c, d)) }
      }

  implicit def product5[P <: Product, A, B, C, D, E](
    implicit m: Mirror.ProductOf[P],
             i: m.MirroredElemTypes =:= (A, B, C, D, E)
    ): Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E } =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E
        def to(p: P): Out = i(Tuple.fromProductTyped(p)) match { case (a, b, c, d, e) => a ~ b ~ c ~ d ~ e }
        def from(o: Out): P = o match { case a ~ b ~ c ~ d ~ e => m.fromProduct((a, b, c, d, e)) }
      }

  implicit def product6[P <: Product, A, B, C, D, E, F](
    implicit m: Mirror.ProductOf[P],
             i: m.MirroredElemTypes =:= (A, B, C, D, E, F)
    ): Twiddler[P] { type Out = A ~ B ~ C ~ D ~ E ~ F } =
      new Twiddler[P] {
        type Out = A ~ B ~ C ~ D ~ E ~ F
        def to(p: P): Out = i(Tuple.fromProductTyped(p)) match { case (a, b, c, d, e, f) => a ~ b ~ c ~ d ~ e ~ f }
        def from(o: Out): P = o match { case a ~ b ~ c ~ d ~ e ~ f => m.fromProduct((a, b, c, d, e, f)) }
      }

  implicit def product7[Pr <: Product, A, B, C, D, E, F, G](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g =>
            mirror.fromProduct((a, b, c, d, e, f, g))
        }
      }
  
  implicit def product8[Pr <: Product, A, B, C, D, E, F, G, H](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h =>
            mirror.fromProduct((a, b, c, d, e, f, g, h))
        }
      }
  
  implicit def product9[Pr <: Product, A, B, C, D, E, F, G, H, I](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i))
        }
      }
      
  implicit def product10[Pr <: Product, A, B, C, D, E, F, G, H, I, J](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j))
        }
      }
  
  implicit def product11[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k))
        }
      }
  
  implicit def product12[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l))
        }
      }
  
  implicit def product13[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m))
        }
      }
  
  implicit def product14[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n))
        }
      }
  
  implicit def product15[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o))
        }
      }
  
  implicit def product16[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p))
        }
      }
  
  implicit def product17[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q))
        }
      }
  
  implicit def product18[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r))
        }
      }
  
  implicit def product19[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s))
        }
      }
  
  implicit def product20[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t))
        }
      }
  
  implicit def product21[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u))
        }
      }
  
  implicit def product22[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v))
        }
      }
  
  implicit def product23[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w))
        }
      }
  
  implicit def product24[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W ~ X } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W ~ X
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w ~ x
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w ~ x =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x))
        }
      }
  
  implicit def product25[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W ~ X ~ Y } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W ~ X ~ Y
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w ~ x ~ y
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w ~ x ~ y =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y))
        }
      }
  
  implicit def product26[Pr <: Product, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z](implicit
        mirror: Mirror.ProductOf[Pr],
        i: mirror.MirroredElemTypes =:= (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z)
    ): Twiddler[Pr] { type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W ~ X ~ Y ~ Z } =
      new Twiddler[Pr] {
        type Out = A ~ B ~ C ~ D ~ E ~ F ~ G ~ H ~ I ~ J ~ K ~ L ~ M ~ N ~ O ~ P ~ Q ~ R ~ S ~ T ~ U ~ V ~ W ~ X ~ Y ~ Z
        def to(p: Pr): Out = i(Tuple.fromProductTyped(p)) match {
          case (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z) => 
            a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w ~ x ~ y ~ z
        }
        def from(o: Out): Pr = o match {
          case a ~ b ~ c ~ d ~ e ~ f ~ g ~ h ~ i ~ j ~ k ~ l ~ m ~ n ~ o ~ p ~ q ~ r ~ s ~ t ~ u ~ v ~ w ~ x ~ y ~ z =>
            mirror.fromProduct((a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w, x, y, z))
        }
      }
}

