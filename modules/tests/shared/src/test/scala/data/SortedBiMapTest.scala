// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import munit.ScalaCheckSuite
import org.scalacheck.Prop

class SortedBiMapTest extends ScalaCheckSuite {

  test("put handles overwrites") {
    val m = SortedBiMap.empty[Int, Int].put(1, 2)
    assertEquals(m.size, 1)
    assertEquals(m.get(1), Some(2))
    assertEquals(m.inverseGet(2), Some(1))

    val m2 = m.put(3, 2)
    assertEquals(m2.size, 1)
    assertEquals(m2.get(3), Some(2))
    assertEquals(m2.inverseGet(2), Some(3))
    assertEquals(m2.get(1), None)

    val m3 = m2.put(3, 4)
    assertEquals(m3.size, 1)
    assertEquals(m3.get(3), Some(4))
    assertEquals(m3.inverseGet(4), Some(3))
    assertEquals(m3.inverseGet(2), None)
  }

  test("entries are sorted") {
    Prop.forAll { (s: Set[Int]) =>
      val m = s.foldLeft(SortedBiMap.empty[Int, String])((acc, i) => acc.put(i, i.toString))
      assertEquals(m.size, s.size)
      assertEquals(m.entries.keySet.toList, s.toList.sorted)
    }
  }
}
