// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._

class SemispaceCacheTest extends ScalaCheckSuite {

  val genEmpty: Gen[SemispaceCache[Int, String]] =
    Gen.choose(-1, 10).map(SemispaceCache.empty)

  test("insert on empty cache results in eviction") {
    val cache = SemispaceCache.empty(0).insert("one", 1)
    assertEquals(cache.values, List.empty)
    assertEquals(cache.lookup("one"), None)
    assertEquals(cache.evicted, List(1))
  }
  
  test("insert on full cache results in eviction") {
    val cache = SemispaceCache.empty(1).insert("one", 1)
    assertEquals(cache.values, List(1))
    assertEquals(cache.lookup("one").map(_._2), Some(1))
    assertEquals(cache.evicted, List.empty)

    // We now have two items (even though that's larger than max)
    val updated = cache.insert("two", 2)
    assert(updated.containsKey("one")) // gen1
    assert(updated.containsKey("two")) // gen0
    assertEquals(updated.evicted, List.empty)
    
    val third = updated.insert("one", 1)
    assert(third.containsKey("one")) // gen1
    assert(third.containsKey("two")) // gen0
    assertEquals(third.evicted, List.empty)
  }

  test("max is never negative") {
    forAll(genEmpty) { c =>
      assert(c.max >= 0)
    }
  }

  test("insert should allow lookup, unless max == 0") {
    forAll(genEmpty) { c =>
      val cʹ = c.insert(1, "x")
      assertEquals(cʹ.lookup(1), if (c.max == 0) None else Some((cʹ, "x")))
    }
  }

  test("overflow") {
    forAll(genEmpty) { c =>
      val max = c.max

      // Load up the cache such that one more insert will cause it to overflow
      val cʹ = (0 until max).foldLeft(c) { case (c, n) => c.insert(n, "x") }
      assertEquals(cʹ.gen0.size, max)
      assertEquals(cʹ.gen1.size, 0)

      // Overflow the cache
      val cʹʹ = cʹ.insert(max, "x")
      assertEquals(cʹʹ.gen0.size, 1 min max)
      assertEquals(cʹʹ.gen1.size, max)

    }
  }

  test("promotion") {
    forAll(genEmpty) { c =>
      val max = c.max

      // Load up the cache such that it overflows by 1
      val cʹ = (0 to max).foldLeft(c) { case (c, n) => c.insert(n, "x") }
      assertEquals(cʹ.gen0.size, 1 min max)
      assertEquals(cʹ.gen1.size, max)

      // Look up something that was demoted.
      cʹ.lookup(0) match {
        case None => assertEquals(max, 0)
        case Some((cʹʹ, _)) =>
          assertEquals(cʹʹ.gen0.size, 2 min max)
          assertEquals(cʹʹ.gen1.size, max)
      }

    }
  }

}
