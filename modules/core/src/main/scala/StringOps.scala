package skunk

import cats.data._
import cats.implicits._
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

class StringOps private[skunk] (sc: StringContext) {

  def sql(): Fragment[Void] =
    Fragment[Void](sc.parts(0), Encoder.void)

  // Encoder[A], Encoder[B], ... => Fragment[A ~ B ~ ...]
  def sql(h: Encoder[_], t: Encoder[_]*): Any =
    macro StringOpsMacros.sql_impl

}

class StringOpsMacros(val c: whitebox.Context) {
  import c.universe._
  def sql_impl(h: Tree, t: Tree*): Tree = {
    val Apply(_, List(Apply(_, parts))) = c.prefix.tree
    val enc  = t.foldLeft(h) { case (a, b) => q"$a.product($b)" }
    val lens = (h +: t).map(t => q"$t.oids.length")
    val sql  = q"skunk.StringOps.mkSql($parts, scala.collection.immutable.List(..$lens, 0))"
    q"skunk.Fragment($sql, $enc)"
  }
}

object StringOps {

  def placeholders(n: Int, i: Int): String =
    List.fill(n)(State((x: Int) => (x + 1, s"$$$x"))).sequence.runA(i).value.mkString(", ")

  def mkSql(parts: List[String], placeholderCounts: List[Int]): String =
    (parts zip placeholderCounts).traverse { case (s, n) =>
      State((i: Int) => (i + n, s + placeholders(n, i)))
    } .runA(1).value.mkString

}

trait ToStringOps {
  implicit def toStringOps(sc: StringContext): StringOps =
    new StringOps(sc)
}
