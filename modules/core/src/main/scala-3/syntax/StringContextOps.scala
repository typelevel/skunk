// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.data.State
import cats.syntax.all._
import scala.language.implicitConversions
import scala.reflect.macros.whitebox
import scala.quoted._
import skunk.data.Identifier
import skunk.util.Origin

class StringContextOps private[skunk](sc: StringContext) {

  /** Construct a constant `Fragment` with no interpolated values. */
  def const()(implicit or: Origin): Fragment[Void] =
    Fragment(sc.parts.toList.map(Left(_)), Void.codec, or)

  /** Construct a constant `AppliedFragment` with no interpolated values. */
  def void()(implicit or: Origin): AppliedFragment =
    const()(or)(Void)

  private[skunk] def internal(literals: String*): Fragment[Void] = {
    val chunks = sc.parts.zipAll(literals, "", "").flatMap { case (a, b) => List(a.asLeft, b.asLeft) }
    Fragment(chunks.toList, Void.codec, Origin.unknown)
  }

}

object StringContextOps {

  sealed trait Part
  case class Str(s: String)                     extends Part
  case class Par(n: State[Int, String])         extends Part // n parameters
  case class Emb(ps: List[Either[String, State[Int, String]]]) extends Part

  def fragmentFromParts[A](ps: List[Part], enc: Encoder[A], or: Origin): Fragment[A] =
    Fragment(
      ps.flatMap {
        case Str(s)  => List(Left(s))
        case Par(n)  => List(Right(n))
        case Emb(ps) => ps
      },
      enc,
      or
    )

  def yell(s: String) = println(s"${Console.RED}$s${Console.RESET}")

  def sqlImpl(sc: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using qc:QuoteContext): Expr[Any] = {

    // Ok we want to construct an Origin here
    val origin = Origin.originImpl(using qc)

    // Our prefix looks like this, and the stringy parts of the interpolation will be a non-empty
    // list of string expressions. We just know this because of the way interpolator desugaring
    // works. If it doesn't work something bad has happened.
    val strings: List[String] =
      sc match {
        case '{ StringContext(${Varargs(Consts(parts))}: _*) } => parts.toList
        case _ =>
          report.error(s"StringContext arguments must be literals.")
          return '{???}
      }

    // The interpolated args are a list of size `parts.length - 1`. We also just know this.
    val args: List[Expr[Any]] = {
      val Varargs(args) = argsExpr
      args.toList
    }

    // Weave the strings and args together, and accumulate a single encoder.
    val lastPart: Expr[Part] = '{Str(${Expr(strings.last)})}
    val (parts, encoders): (List[Expr[Part]], List[Expr[Any]]) =
      (strings zip args).foldRight((List[Expr[Part]](lastPart), List.empty[Expr[Any]])) {

      case ((str, arg), (parts, es)) =>

        if (str.endsWith("#")) {

          // Interpolations like "...#$foo ..." require `foo` to be a String.
          arg match {
            case '{ $s: String } => ('{Str(${Expr(str.dropRight(1))})} :: '{Str($s)} :: parts, es)
            case '{ $a: $T }     =>
              report.error(s"Found ${Type[T].show}, expected String.}", a)
              return '{???} ///
          }

        } else {

          arg match {

            // The interpolated thing is an Encoder.
            case '{ $e: Encoder[$T] } =>
              val newParts    = '{Str(${Expr(str)})} :: '{Par($e.sql)} :: parts
              val newEncoders = '{ $e : Encoder[T] } :: es
              (newParts, newEncoders)

            // The interpolated thing is a Fragment[Void]
            case '{ $f: Fragment[Void] } =>
              val newParts    = '{Str(${Expr(str)})} :: '{Emb($f.parts)} :: parts
              (newParts, es)

            // The interpolated thing is a Fragment[A] for some A other than Void
            case '{ $f: Fragment[$A] } =>
              val newParts    = '{Str(${Expr(str)})} :: '{Emb($f.parts)} :: parts
              val newEncoders = '{ $f.encoder : Encoder[A] } :: es
              (newParts, newEncoders)

            case '{ $a: $T } =>
              report.error(s"Found ${Type[T].show}, expected String, Encoder, or Fragment.", a)
              return '{???}

          }

        }

    }

    val finalEnc: Expr[Any] =
      if (encoders.isEmpty) '{ Void.codec }
      else encoders.reduceLeft {
        case ('{$a : Encoder[$A]}, '{ $b : Encoder[$B] }) => '{$a ~ $b}
      }

    finalEnc match {
      case '{ $e : Encoder[$T] } => '{ fragmentFromParts[T](${Expr.ofList(parts)}, $e, $origin) }
    }

  }

  def idImpl(sc: Expr[StringContext])(using qc:QuoteContext): Expr[Identifier] =
    sc match {
      case '{ StringContext(${Varargs(Consts(Seq(part)))}: _*) } =>
        Identifier.fromString(part) match {
          case Right(Identifier(s)) => '{ Identifier.fromString(${Expr(s)}).fold(sys.error, identity) }
          case Left(s) =>
            report.error(s)
            return '{???}
        }
      case _ =>
        report.error(s"Identifiers cannot have interpolated arguments")
        return '{???}
    }

}

trait ToStringContextOps {

  extension (inline sc: StringContext) transparent inline def sql(inline args: Any*): Any =
    ${ StringContextOps.sqlImpl('sc, 'args) }

  extension (inline sc: StringContext) inline def id(): Identifier =
    ${ StringContextOps.idImpl('sc) }

  implicit def toStringOps(sc: StringContext): StringContextOps =
    new StringContextOps(sc)
}

object stringcontext extends ToStringContextOps