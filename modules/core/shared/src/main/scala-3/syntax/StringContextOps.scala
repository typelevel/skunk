// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package syntax

import cats.data.State
import cats.syntax.all._
import scala.language.implicitConversions
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

  @annotation.nowarn
  def sqlImpl(sc: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using qc: Quotes): Expr[Any] = {
    import qc.reflect.report

    // Ok we want to construct an Origin here
    val origin = '{
      val sp = ${org.tpolecat.sourcepos.SourcePosPlatform.sourcePos_impl(using qc)}
      Origin(sp.file, sp.line)
    }

    // Our prefix looks like this, and the stringy parts of the interpolation will be a non-empty
    // list of string expressions. We just know this because of the way interpolator desugaring
    // works. If it doesn't work something bad has happened.
    val strings: Either[Expr[Any], List[String]] =
      sc match {
        case '{ StringContext(${Varargs(Exprs(parts))}: _*) } => Right(parts.toList)
        case _ =>
          Left('{ compiletime.error(s"StringContext arguments must be literals.") })
      }

    // The interpolated args are a list of size `parts.length - 1`. We also just know this.
    val args: List[Expr[Any]] = {
      val Varargs(args) = argsExpr: @unchecked // we just know this. right?
      args.toList
    }

    // Weave the strings and args together, and accumulate a single encoder.
    val partsEncoders: Either[Expr[Any], (List[Expr[Part]], List[Expr[Any]])] = strings.flatMap { strings =>
      val lastPart: Expr[Part] = '{Str(${Expr(strings.last)})}
      (strings zip args).reverse.foldLeftM((List[Expr[Part]](lastPart), List.empty[Expr[Any]])) {
        case ((parts, es), (str, arg)) =>
          if (str.endsWith("#")) then {
            // Interpolations like "...#$foo ..." require `foo` to be a String.
            arg match {
              case '{ $s: String } => Right(('{Str(${Expr(str.dropRight(1))})} :: '{Str($s)} :: parts, es))
              case '{ $a: t }     =>
                report.error(s"Found ${Type.show[t]}, expected String.}", a)
                Left('{ compiletime.error("Expected String") }) ///
            }
          } else {
            arg match {
              // The interpolated thing is an Encoder.
              case '{ $e: Encoder[t] } =>
                val newParts    = '{Str(${Expr(str)})} :: '{Par($e.sql)} :: parts
                val newEncoders = '{ $e : Encoder[t] } :: es
                Right((newParts, newEncoders))

              // The interpolated thing is a Fragment[Void]
              case '{ $f: Fragment[Void] } =>
                val newParts    = '{Str(${Expr(str)})} :: '{Emb($f.parts)} :: parts
                Right((newParts, es))

              // The interpolated thing is a Fragment[A] for some A other than Void
              case '{ $f: Fragment[a] } =>
                val newParts    = '{Str(${Expr(str)})} :: '{Emb($f.parts)} :: parts
                val newEncoders = '{ $f.encoder : Encoder[a] } :: es
                Right((newParts, newEncoders))

              case '{ $a: t } =>
                report.error(s"Found ${Type.show[t]}, expected String, Encoder, or Fragment.", a)
                Left('{compiletime.error("Expected String, Encoder, or Fragment.")})
            }
          }
      }
    }

    val legacyCommandSyntax = Expr.summon[skunk.featureFlags.legacyCommandSyntax].isDefined
    partsEncoders.map { (parts, encoders) =>
      val finalEnc: Expr[Any] =
        if encoders.isEmpty then '{ Void.codec }
        else if legacyCommandSyntax then
          encoders.reduceLeft {
            case ('{$a : Encoder[a]}, '{ $b : Encoder[b] }) => '{$a ~ $b}
          }
        else if encoders.size == 1 then encoders.head
        else {
          val last: Expr[Any] = encoders.last match {
            case '{$a: Encoder[a]} => '{$a.imap(_ *: EmptyTuple)(_.head)}
          }
          encoders.init.foldRight(last) { case ('{$a: Encoder[a]}, '{$acc: Encoder[t & Tuple]}) =>
            // TODO Should be able to use *: but as of twiddles 1.0.0-RC2 that no longer works; see https://github.com/typelevel/twiddles/issues/146
            '{_root_.org.typelevel.twiddles.Twiddles.prepend($a, $acc)}
          }
        }

      finalEnc match {
        case '{ $e : Encoder[t] } => '{ fragmentFromParts[t](${Expr.ofList(parts)}, $e, $origin) }
      }
    }.merge
  }

  def idImpl(sc: Expr[StringContext])(using qc: Quotes): Expr[Identifier] =
    import qc.reflect.report
    sc match {
      case '{ StringContext(${Varargs(Exprs(Seq(part)))}: _*) } =>
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
