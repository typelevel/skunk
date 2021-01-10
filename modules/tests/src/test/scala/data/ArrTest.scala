// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package data

import cats.syntax.all._
import skunk.data.Arr
import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import org.scalacheck.Prop._
import org.scalacheck.Cogen
import munit.DisciplineSuite
import cats.laws.discipline.TraverseTests
import cats.kernel.laws.discipline.EqTests
import scala.collection.compat._

class ArrTest extends DisciplineSuite {

  val MaxDims  = 4
  val MaxElems = 3

  def genArr[A](genA: Gen[A]): Gen[Arr[A]] =
    Gen.choose(0, MaxDims).flatMap {
      case 0 => Arr.empty[A]
      case n =>
        for {
          dims <- Gen.listOfN(n, Gen.choose(1, MaxElems))
          data <- Gen.listOfN(dims.product, genA)
        } yield Arr(data: _*).reshape(dims: _*).get
    }

  implicit def arbArr[A: Arbitrary]: Arbitrary[Arr[A]] =
    Arbitrary(genArr(Arbitrary.arbitrary[A]))

  implicit def cogArr[A: Cogen]: Cogen[Arr[A]] =
    Cogen[(List[A], List[Int])].contramap(arr => (arr.flattenTo(List), arr.dimensions))

  checkAll("Arr.EqLaws", EqTests[Arr[Int]].eqv)
  checkAll("Arr.TraverseLaws", TraverseTests[Arr].traverse[Int, Int, Int, Set[Int], Option, Option])

  property("size == 0 <=> isEmpty") {
    forAll { (arr: Arr[Byte]) =>
      assertEquals(arr.size == 0, arr.isEmpty)
    }
  }

  property("size == 0 <=> dimensions.isEmpty") {
    forAll { (arr: Arr[Byte]) =>
      assertEquals(arr.size == 0, arr.dimensions.isEmpty)
    }
  }

  property("size != 0 <=> size == dimensions.product") {
    forAll { (arr: Arr[Byte]) =>
      assertEquals(arr.size != 0, arr.size == arr.dimensions.product)
    }
  }

  property("flatten round-trip") {
    forAll { (arr: Arr[Byte]) =>
      val data    = arr.flattenTo(List)
      val rebuilt = Arr(data: _*).reshape(arr.dimensions: _*)
      assert(rebuilt === Some(arr))
    }
  }

  property("reshape fails unless dimensions factor length") {
    forAll { (arr: Arr[Byte]) =>
      assertEquals(arr.reshape(3 :: arr.dimensions : _*), None)
    }
  }

  property("can't reshape to nothing unless array is empty") {
    forAll { (arr: Arr[Byte]) =>
      assertEquals(arr.isEmpty, arr.reshape().isDefined)
    }
  }

  property("flattenTo consistent with reshape(length)") {
    forAll { (arr: Arr[Byte]) =>
      val flat     = arr.flattenTo(Array)
      val reshaped = arr.reshape(arr.size).get
      (0 to flat.size).foreach { i =>
        assertEquals(flat.lift(i), reshaped.get(i))
      }
    }
  }

  property("encode/parseWith round-trip") {
    forAll { (arr: Arr[Byte]) =>
      val encoded = arr.encode(_.toString)
      val decoded = Arr.parseWith(_.toByte.asRight)(encoded)
      assert(decoded === Right(arr))
    }
  }

  property("encode/parse round-trip (String)") {
    forAll { (arr: Arr[String]) =>
      val encoded = arr.encode(identity)
      val decoded = Arr.parse(encoded)
      assert(decoded === Right(arr))
    }
  }

  test("get consistent with indexing") {

    // A Scala multi-dimensional array of size (3)(2)(1)
    val data: List[List[List[Int]]] =
      List(
        List(List(0), List(1)),
        List(List(2), List(3)),
        List(List(4), List(5)),
      )

    // As an arr of dimensions 3, 2, 1
    val arr: Arr[Int] =
      Arr(data.flatMap(_.flatten): _*).reshape(3,2,1).get

    // Pairwise equivalence
    val same: IndexedSeq[Boolean] =
      for {
        x <- 0 until 3
        y <- 0 until 2
      } yield arr.get(x, y, 0) == Some(data(x)(y)(0))

    // Should all be true!
    assert(same.forall(identity))

  }

  property("get fails if wrong number of indices") {
    forAll { (arr: Arr[Byte]) =>
      assertEquals(arr.get(0,0).isDefined, arr.dimensions.length == 2)
    }
  }

  test("parse failure: unterminated") {
    assertEquals(Arr.parse("{1,2"), Left("unterminated array literal"))
  }

  test("parse failure: non-rectangular (slice too large)") {
    assertEquals(Arr.parse("{{1},{2,3}}"), Left("parse error at index 7: expected 1 element(s) here; found more"))
  }

  test("parse failure: non-rectangular (slice too small)") {
    assertEquals(Arr.parse("{{1,2},{3}}"), Left("parse error at index 9: expected 2 element here, only found 1"))
  }

  test("parse failure: non-rectangular (data above leaf level)") {
    assertEquals(Arr.parse("{{1},2}"), Left("parse error at index 5: expected '{', found 2"))
  }

  test("parse failure: extra data") {
    assertEquals(Arr.parse("{1}foo"), Left("parse error at index 3: expected end of string, found f"))
  }

  test("parse failure: expected datum") {
    assertEquals(Arr.parse("{1,}"), Left("parse error at index 3: expected datum, found '}'"))
  }

  test("parse failure: malformed datum") {
    assertEquals(Arr.parse("{abc,de{f,ghi}"), Left("parse error at index 7: illegal character in unquoted datum: '{'"))
  }

  test("parse failure: missing terminator") {
    assertEquals(Arr.parse("{{abc}x"), Left("parse error at index 6: expected ',' or '}', found x"))
  }

  test("parse failure: null element") {
    assertEquals(Arr.parse("{foo,bar,null}"), Left("parse error at index 13: encountered NULL array element (currently unsupported)"))
  }

  test("quoted null (ok)") {
    assert(Arr.parse("{foo,bar,\"null\"}") === Right(Arr("foo", "bar", "null")))
  }

}

