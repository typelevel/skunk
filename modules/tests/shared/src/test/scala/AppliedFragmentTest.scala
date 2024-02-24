// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.syntax.all._
import skunk.implicits._
import skunk.codec.all._
import skunk._

class AppliedFragmentTest extends ffstest.FTest {

  // We check associativity and identity with respect to SQL construction. The rest follows from
  // the types, I think. There's not really any other way to make it compile.

  pureTest("associativity") {
    val a = sql"a1 $int4 a2".apply(42)
    val b = sql"b1 $bool b2".apply(true)
    val c = sql"c1 $varchar c2".apply("foo")
    val af1 = ((a combine b) combine c)
    val af2 = (a combine (b combine c))
    af1.fragment.sql === af2.fragment.sql

  }

  pureTest("left identity") {
    val a = sql"a1 $int4 a2".apply(42)
    val af1 = a
    val af2 = AppliedFragment.empty combine a
    af1.fragment.sql === af2.fragment.sql
  }

  pureTest("right identity") {
    val a = sql"a1 $int4 a2".apply(42)
    val af1 = a
    val af2 = a |+| AppliedFragment.empty
    af1.fragment.sql === af2.fragment.sql
  }

  pureTest("void syntax") {
    val f = void"foo"
    (f.fragment.sql, f.fragment.encoder, f.argument) == (("foo", Void.codec, Void))
  }

  pureTest("const syntax") {
    val f = const"foo"
    (f.sql, f.encoder) == (("foo", Void.codec))
  }

}