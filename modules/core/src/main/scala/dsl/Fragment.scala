package skunk
package dsl

import cats.data._
import shapeless.HList

case class Fragment[H <: HList](parts: Chain[String], oids: List[List[Type]], encoder: Encoder[H]) {

  def sql: String = {

    def go(ps: List[String], oids: List[List[Type]], n: Int, acc: String): String =
      (ps, oids) match {
        case (p :: ps, ts :: oids) =>
          val add = p + ts.zipWithIndex.map { case (_, i) => s"$$${i + n}" } .mkString(", ")
          go(ps, oids, n + ts.length, acc + add)
        case (p :: Nil, Nil) => acc + p
        case _ => sys.error("huh?")
      }

    s"-- ./modules/core/src/main/scala/dsl/Test.scala:8\n" + go(parts.toList, oids, 1, "")

  }


  override def toString = {

    def go(ps: List[String], oids: List[List[Type]], n: Int, acc: String): String =
      (ps, oids) match {
        case (p :: ps, ts :: oids) =>
          val add = p + ts.zipWithIndex.map { case (t, i) => s"$$${i + n}::${t.name}" } .mkString(", ")
          go(ps, oids, n + ts.length, acc + add)
        case (p :: Nil, Nil) => acc + p
        case _ => sys.error("huh?")
      }

    s"-- ./modules/core/src/main/scala/dsl/Test.scala:8\n" + go(parts.toList, oids, 1, "")

  }

}
