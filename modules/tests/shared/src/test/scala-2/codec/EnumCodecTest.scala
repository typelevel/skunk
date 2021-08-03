// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.Eq
import skunk._
import skunk.codec.`enum`._
import skunk.data.Type
import enumeratum._
import enumeratum.EnumEntry.Lowercase

abstract class EnumCodecTest extends CodecTest(strategy = Strategy.SearchPath) {

  // Case 1: enum defined normally
  sealed abstract class Case1(val label: String)
  object Case1 {
    case object Foo extends Case1("foo")
    case object Bar extends Case1("bar")
    val values = List(Foo, Bar)
    def fromLabel(label: String): Option[Case1] = values.find(_.label == label)
    implicit val EqMyEnum: Eq[Case1] = Eq.fromUniversalEquals
  }
  val case1 = `enum`[Case1](_.label, Case1.fromLabel, Type("myenum"))
  roundtripTest(case1)(Case1.Foo, Case1.Bar)
  decodeFailureTest(case1, List("woozle"))

  // Case 1: enum defined with Enumeratum
  sealed trait Case2 extends EnumEntry with Lowercase
  object Case2 extends enumeratum.Enum[Case2] {
    case object Foo extends Case2
    case object Bar extends Case2
    val values = findValues
    implicit val EqCase2: Eq[Case2] = Eq.fromUniversalEquals
  }
  val case2 = `enum`(Case2, Type("myenum"))
  roundtripTest(case2)(Case2.Foo, Case2.Bar)
  decodeFailureTest(case2, List("woozle"))

}


