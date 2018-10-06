package skunk

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class StringOps private[skunk] (sc: StringContext) {
  void(sc)

  def sql(argSeq: Any*): Any =
    macro StringOpsMacros.sql_impl

  def id(): Identifier =
    macro StringOpsMacros.identifier_impl

}

class StringOpsMacros(val c: whitebox.Context) {
  import c.universe._

  def sql_impl(argSeq: Tree*): Tree = {

    // Our prefix looks like this, and the stringy parts of the interpolation will be a non-empty
    // list of string literal trees. We just know this because of the way interpolator desugaring
    // works. If it doesn't work something bad has happened.
    val parts: List[Tree] =
      c.prefix.tree match {
        case Apply(_, List(Apply(_, ts))) => ts
        case _ => c.abort(c.prefix.tree.pos, "Unexpected tree, oops. See StringOps.scala")
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
    val (finalParts, preliminaryEncoders) : (List[Tree /* part */], List[Tree] /* encoder */) =
      (parts zip args).foldRight((List(q"skunk.StringOps.Str(${parts.last})"), List.empty[Tree])) {

        // The stringy part had better be a string literal. If we got here via the interpolator it
        // always will be. If not we punt (below).
        case ((part @ Literal(Constant(str: String)), arg), (tail, es)) =>

          // The arg had better have a type conforming with Encoder[_] or String
          val argType = c.typecheck(arg, c.TYPEmode).tpe

          if (str.endsWith("#")) {

            // The stringy part ends in a `#` so the following arg must typecheck as a String.
            // Assuming it does, turn it into a string and "emit" two `Left`s.
            if (argType <:< StringType) {
              val p1 = q"skunk.StringOps.Str(${Literal(Constant(str.init))} concat $arg)"
              (p1 :: tail, es)
            } else
              c.abort(arg.pos, s"type mismatch;\n  found   : $argType\n  required: $StringType")

          } else if (argType <:< EncoderType) {

              val p1 = q"skunk.StringOps.Str($part)"
              val p2 = q"skunk.StringOps.Par($arg.oids.length)"
              (p1 :: p2 :: tail, arg :: es)

          } else if (argType <:< VoidFragmentType) {

              val p1 = q"skunk.StringOps.Str($part)"
              val p2 = q"skunk.StringOps.Emb($arg.parts)"
              (p1 :: p2 :: tail, es)

          } else if (argType <:< FragmentType) {

              val p1 = q"skunk.StringOps.Str($part)"
              val p2 = q"skunk.StringOps.Emb($arg.parts)"
              (p1 :: p2 :: tail, q"$arg.encoder" :: es)

          } else {

            c.abort(arg.pos, s"type mismatch;\n  found   : $argType\n  required: $EncoderType or $FragmentType")

          }

        // Otherwise the stringy part isn't a string literal, which means someone has gotten here
        // through nefarious means, like constructing a StringContext by hand.
        case ((p, _), _) =>
          c.abort(p.pos, s"StringContext parts must be string literals.")

      }

    // The final encoder is either `Encoder.void` or `a ~ b ~ ...`
    val finalEncoder: Tree =
      if (preliminaryEncoders.isEmpty) q"skunk.Encoder.void"
      else preliminaryEncoders.reduceLeft((a, b) => q"$a ~ $b") // note: must be left-associated

    // We now have what we need to construct a fragment.
    q"skunk.StringOps.fragmentFromParts($finalParts, $finalEncoder)"
    // q"skunk.Fragment($finalParts, $finalEncoder)"

  }

  def identifier_impl(): Tree = {
    val Apply(_, List(Apply(_, List(s @ Literal(Constant(part: String)))))) = c.prefix.tree
    Identifier.fromString(part) match {
      case Left(s) => c.abort(c.enclosingPosition, s)
      case Right(Identifier(s)) => q"skunk.Identifier.unsafeFromString($s)"
    }
  }

}

object StringOps {

  sealed trait Part
  case class Str(s: String)                     extends Part
  case class Par(n: Int)                        extends Part
  case class Emb(ps: List[Either[String, Int]]) extends Part

  def fragmentFromParts[A](ps: List[Part], enc: Encoder[A]): Fragment[A] =
    Fragment(
      ps.flatMap {
        case Str(s)  => List(Left(s))
        case Par(n)  => List(Right(n))
        case Emb(ps) => ps
      },
      enc
    )

}

trait ToStringOps {
  implicit def toStringOps(sc: StringContext): StringOps =
    new StringOps(sc)
}
