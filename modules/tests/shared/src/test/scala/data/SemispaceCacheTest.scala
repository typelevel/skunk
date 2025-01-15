// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop._

class SemispaceCacheTest extends ScalaCheckSuite {

  val genEmpty: Gen[SemispaceCache[Int, String]] =
    Gen.choose(-1, 10).map(SemispaceCache.empty(_, true))
  
  test("eviction should never contain values in gen0/gen1") {
    val cache = SemispaceCache.empty(2, true).insert("one", 1)

    val i1 = cache.insert("one", 1)
    // Two doesn't exist; space in gen0, insert
    val i2 = i1.lookup("two").map(_._1).getOrElse(i1.insert("two", 2))
    assertEquals(i2.gen0, Map("one" -> 1, "two" -> 2))
    assertEquals(i2.gen1, Map.empty[String, Int])
    assertEquals(i2.evicted.toList, Nil)
    
    // Three doesn't exist, hit max; slide gen0 -> gen1 and add to gen0
    val i3 = i2.lookup("three").map(_._1).getOrElse(i2.insert("three", 3))
    assertEquals(i3.gen0, Map("three" -> 3))
    assertEquals(i3.gen1, Map("one" -> 1, "two" -> 2))
    assertEquals(i3.evicted.toList, Nil)
    
    // One exists in gen1; pull up to gen0 and REMOVE from gen1
    val i4 = i3.lookup("one").map(_._1).getOrElse(i3.insert("one", 1))
    assertEquals(i4.gen0, Map("one" -> 1, "three" -> 3))
    assertEquals(i4.gen1, Map("two" -> 2))
    assertEquals(i4.evicted.toList, Nil)
    
    // Four doesn't exist; gen0 is full so push to gen1
    // insert four to gen0 and evict gen1
    val i5 = i4.lookup("four").map(_._1).getOrElse(i4.insert("four", 4))
    assertEquals(i5.gen0, Map("four" -> 4))
    assertEquals(i5.gen1, Map("one" -> 1, "three" -> 3))
    assertEquals(i5.evicted.toList, List(2))
  }

  test("insert on empty cache results in eviction") {
    val cache = SemispaceCache.empty(0, true).insert("one", 1)
    assertEquals(cache.values, Nil)
    assert(!cache.containsKey("one"))
    assertEquals(cache.clearEvicted._2, List(1))
  }
  
  test("insert on full cache results in eviction") {
    val cache = SemispaceCache.empty(1, true).insert("one", 1)
    assertEquals(cache.values, List(1))
    assertEquals(cache.lookup("one").map(_._2), Some(1))
    assertEquals(cache.clearEvicted._2, List.empty)

    // We now have two items (the cache stores up to 2*max entries)
    val updated = cache.insert("two", 2)
    assert(updated.containsKey("one")) // gen1
    assert(updated.containsKey("two")) // gen0
    assertEquals(updated.clearEvicted._2, List.empty)
    
    val third = updated.insert("one", 1)
    assert(third.containsKey("one")) // gen1
    assert(third.containsKey("two")) // gen0
    assertEquals(third.clearEvicted._2, List.empty)
  }

  test("max is never negative") {
    forAll(genEmpty) { c =>
      assert(c.max >= 0)
    }
  }

  test("insert should allow lookup") {
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
      val cʹ = (0 to max).foldLeft(c) { case (c, n) => c.insert(n, n.toString) }
      assertEquals(cʹ.gen0.size, 1 min max)
      assertEquals(cʹ.gen1.size, max)

      // Look up something that was demoted.
      cʹ.lookup(0) match {
        case None => assertEquals(max, 0)
        case Some((cʹʹ, _)) =>
          assertEquals(cʹʹ.gen0.size, 2 min max)
          // When we promote 0 to gen0, we remove it from gen1
          assertEquals(cʹʹ.gen1.size, max-1 max 1)
          assertEquals(cʹʹ.evicted.toList, Nil)
      }

    }
  }

}
