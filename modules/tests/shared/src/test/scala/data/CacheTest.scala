// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class CacheTest extends ScalaCheckSuite {

  val genEmpty: Gen[Cache[Int, String]] =
    Gen.choose(-1, 10).map(Cache.empty)

  test("insert on empty cache results in eviction") {
    val cache = Cache.empty(0).put("one", 1)._1
    assertEquals(cache.values.toList, Nil)
    assert(!cache.contains("one"))
  }

  test("max is never negative") {
    forAll(genEmpty) { c =>
      assert(c.max >= 0)
    }
  }

  test("insert should allow get") {
    forAll(genEmpty) { c =>
      val cʹ = c.put(1, "x")._1
      assertEquals(cʹ.get(1), if (c.max == 0) None else Some((cʹ, "x")))
    }
  }

  test("eviction") {
    forAll(genEmpty) { c =>
      val max = c.max

      // Load up the cache such that one more insert will cause it to overflow
      val c1 = (0 until max).foldLeft(c) { case (c, n) => c.put(n, "x")._1 }
      assertEquals(c1.values.size, max)

      // Overflow the cache
      val (c2, evicted) = c1.put(max, "x")
      assertEquals(c2.values.size, max)
      assertEquals(evicted, Some(0 -> "x"))

      if (max > 2) {
        // Access oldest element
        val c3 = c2.get(1).get._1

        // Insert another element and make sure oldest element is not the element evicted
        val (c4, evicted1) = c3.put(max + 1, "x")
        assertEquals(evicted1, Some(2 -> "x"))
      }
    }
  }
  
  test("eviction 2") {
    val c1 = Cache.empty(2).put("one", 1)._1
    assertEquals(c1.values.toList, List(1))
    assertEquals(c1.get("one").map(_._2), Some(1))

    val (c2, evicted2) = c1.put("two", 2)
    assert(c2.contains("one"))
    assert(c2.contains("two"))
    assertEquals(evicted2, None)
    
    val (c3, evicted3) = c2.put("one", 1)
    assert(c3.contains("one"))
    assert(c3.contains("two"))
    assertEquals(evicted3, None)

    val (c4, evicted4) = c2.put("one", 0)
    assert(c4.contains("one"))
    assert(c4.contains("two"))
    assertEquals(evicted4, Some("one" -> 1))
  }
}
