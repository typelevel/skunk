// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package data

import skunk.data.LTree

class LTreeTest extends ffstest.FTest {

  lazy val foo = LTree.fromLabels("foo").toOption.get
  lazy val foobar = LTree.fromLabels("foo", "bar").toOption.get

  test("LTree parsing") {
    assertEquals(LTree.fromString("").getOrElse(fail("Failed to parse empty LTree")), LTree.Empty)

    assert(LTree.fromString("abc.d!f").isLeft, "regex failed")
    assert(LTree.fromString("abc.d_f").isRight, "regex failed")
    assert(LTree.fromString("abc1.d_f2").isRight, "regex failed")
    assert(LTree.fromString("foo.βar.baΣΩ").isRight, "regex failed")
    assert(LTree.fromString("foo.βar.❤").isLeft, "regex failed")

    assert(LTree.fromString(List.fill(LTree.MaxTreeLength)("a").mkString(".")).isRight, "max tree len failed")
    assert(LTree.fromString(List.fill(LTree.MaxTreeLength + 1)("a").mkString(".")).isLeft, "max tree len failed")

    assert(LTree.fromString(List.fill(3)("a" * LTree.MaxLabelLength).mkString(".")).isRight, "max label len failed")
    assert(LTree.fromString(List.fill(3)("a" * LTree.MaxLabelLength + 1).mkString(".")).isLeft, "max label len failed")
  }

  test("LTree.isAncestorOf") {
    assert(LTree.Empty.isAncestorOf(foo))
    assert(foo.isAncestorOf(foo))
    assert(foo.isAncestorOf(foobar))

    assert(!foo.isAncestorOf(LTree.Empty))
    assert(!foobar.isAncestorOf(foo))
  }

  test("LTree.isDescendantOf") {
    assert(foo.isDescendantOf(LTree.Empty))
    assert(foobar.isDescendantOf(foo))
  }

}
