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
