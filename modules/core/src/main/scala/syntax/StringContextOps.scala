// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import skunk.data.Identifier
import skunk.util.Origin

class StringContextOps private[skunk](sc: StringContext) {
  void(sc)

  def sql(argSeq: Any*): Any =
    macro StringContextOps.StringOpsMacros.sql_impl

  def id(): Identifier =
    macro StringContextOps.StringOpsMacros.identifier_impl

}

object StringContextOps {

  sealed trait Part
  case class Str(s: String)                     extends Part
  case class Par(n: Int)                        extends Part
  case class Emb(ps: List[Either[String, Int]]) extends Part

  def fragmentFromParts[A](ps: List[Part], enc: Encoder[A], or: Origin): Fragment[A] =
    Fragment(
      ps.flatMap {
        case Str(s)  => List(Left(s))
        case Par(n)  => List(Right(n))
        case Emb(ps) => ps
      },
      enc,
      Some(or)
    )

  class StringOpsMacros(val c: whitebox.Context) {
    import c.universe._

    def sql_impl(argSeq: Tree*): Tree = {

      // Ok we want to construct an Origin here
      val file   = c.enclosingPosition.source.path
      val line   = c.enclosingPosition.line
      val origin = q"_root_.skunk.util.Origin($file, $line)"

      // Our prefix looks like this, and the stringy parts of the interpolation will be a non-empty
      // list of string literal trees. We just know this because of the way interpolator desugaring
      // works. If it doesn't work something bad has happened.
      val parts: List[Tree] =
        c.prefix.tree match {
          case Apply(_, List(Apply(_, ts))) => ts
          case _ => c.abort(c.prefix.tree.pos, "Unexpected tree, oops. See StringContextOps.scala")
        }

      // The interpolated args are a list of size `parts.length - 1`. We also just know this.
      val args = argSeq.toList

      // Every arg must conform with Encoder[_] or String
      val EncoderType      = typeOf[Encoder[_]]
      val VoidFragmentType = typeOf[Fragment[Void]]
      val FragmentType     = typeOf[Fragment[_]]
      val StringType       = typeOf[String]

      // Assemble a single list of Either[string tree, encoder int] by interleaving the stringy parts
      // and the args' lengths, as well as a list of the args. If the arg is an interpolated string
      // we reinterpret it as a stringy part. If the arg is a fragment we splice it in.
      val (finalParts, encoders) : (List[Tree /* part */], List[Tree] /* encoder */) =
        (parts zip args).foldRight((List(q"_root_.skunk.syntax.StringContextOps.Str(${parts.last})"), List.empty[Tree])) {

          // The stringy part had better be a string literal. If we got here via the interpolator it
          // always will be. If not we punt (below).
          case ((part @ Literal(Constant(str: String)), arg), (tail, es)) =>

            // The arg had better have a type conforming with Encoder[_] or String
            val argType = c.typecheck(arg, c.TYPEmode).tpe

            if (str.endsWith("#")) {

              // The stringy part ends in a `#` so the following arg must typecheck as a String.
              // Assuming it does, turn it into a string and "emit" two `Left`s.
              if (argType <:< StringType) {
                val p1 = q"_root_.skunk.syntax.StringContextOps.Str(${str.init}.concat($arg))"
                (p1 :: tail, es)
              } else
                c.abort(arg.pos, s"type mismatch;\n  found   : $argType\n  required: $StringType")

            } else if (argType <:< EncoderType) {

                val p1 = q"_root_.skunk.syntax.StringContextOps.Str($part)"
                val p2 = q"_root_.skunk.syntax.StringContextOps.Par($arg.types.length)"
                (p1 :: p2 :: tail, arg :: es)

            } else if (argType <:< VoidFragmentType) {

                val p1 = q"_root_.skunk.syntax.StringContextOps.Str($part)"
                val p2 = q"_root_.skunk.syntax.StringContextOps.Emb($arg.parts)"
                (p1 :: p2 :: tail, es)

            } else if (argType <:< FragmentType) {

                val p1 = q"_root_.skunk.syntax.StringContextOps.Str($part)"
                val p2 = q"_root_.skunk.syntax.StringContextOps.Emb($arg.parts)"
                (p1 :: p2 :: tail, q"$arg.encoder" :: es)

            } else {

              c.abort(arg.pos, s"type mismatch;\n  found   : $argType\n  required: $EncoderType or $FragmentType")

            }

          // Otherwise the stringy part isn't a string literal, which means someone has gotten here
          // through nefarious means, like constructing a StringContext by hand.
          case ((p, _), _) =>
            c.abort(p.pos, s"StringContext parts must be string literals.")

        }

      // The final encoder is either `Void.codec` or `a ~ b ~ ...`
      val finalEncoder: Tree =
        encoders.reduceLeftOption((a, b) => q"$a ~ $b").getOrElse(q"_root_.skunk.Void.codec")

      // We now have what we need to construct a fragment.
      q"_root_.skunk.syntax.StringContextOps.fragmentFromParts($finalParts, $finalEncoder, $origin)"

    }

    def identifier_impl(): Tree = {
      val Apply(_, List(Apply(_, List(s @ Literal(Constant(part: String)))))) = c.prefix.tree
      Identifier.fromString(part) match {
        case Left(s) => c.abort(c.enclosingPosition, s)
        case Right(Identifier(s)) => q"_root_.skunk.data.Identifier.unsafeFromString($s)"
      }
    }

  }

}

trait ToStringContextOps {
  implicit def toStringOps(sc: StringContext): StringContextOps =
    new StringContextOps(sc)
}

object stringcontext extends ToStringContextOps