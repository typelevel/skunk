// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package data

import skunk.data.LTree

class LTreeTest extends munit.FunSuite {

  test("LTree parsing") {
    assertEquals(LTree.fromString("").getOrElse(fail("Failed to parse empty LTree")), LTree.Empty)

    assert(LTree.fromString("abc.d!f").isLeft, "regex failed")
    assert(LTree.fromString("abc.d_f").isRight, "regex failed")

    assert(LTree.fromString(List.fill(LTree.MaxTreeLength)("a").mkString(".")).isRight, "max tree len failed")
    assert(LTree.fromString(List.fill(LTree.MaxTreeLength + 1)("a").mkString(".")).isLeft, "max tree len failed")
    
    assert(LTree.fromString(List.fill(3)("a" * LTree.MaxLabelLength).mkString(".")).isRight, "max label len failed")
    assert(LTree.fromString(List.fill(3)("a" * LTree.MaxLabelLength + 1).mkString(".")).isLeft, "max label len failed")
  }

  test("LTree.isAncestorOf") {
    assert(LTree.Empty.isAncestorOf(LTree.unsafe("foo")))
    assert(LTree.unsafe("foo").isAncestorOf(LTree.unsafe("foo")))
    assert(LTree.unsafe("foo").isAncestorOf(LTree.unsafe("foo", "bar")))

    assert(!LTree.unsafe("foo").isAncestorOf(LTree.Empty))
    assert(!LTree.unsafe("foo", "bar").isAncestorOf(LTree.unsafe("foo")))
  }

  test("LTree.isDescendantOf") {
    assert(LTree.unsafe("foo").isDescendantOf(LTree.Empty))
    assert(LTree.unsafe("foo", "bar").isDescendantOf(LTree.unsafe("foo")))
  }
}
