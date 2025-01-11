// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.syntax.all._

/**
 * Cache based on a two-generation GC.
 * Taken from https://twitter.com/pchiusano/status/1260255494519865346
 */
sealed abstract case class SemispaceCache[K, V](gen0: Map[K, V], gen1: Map[K, V], max: Int, possiblyEvicted: List[V]) {

  assert(max > 0) // nb: enforced in empty constructor
  assert(gen0.size <= max)
  assert(gen1.size <= max)

  def insert(k: K, v: V): SemispaceCache[K, V] =
    if (gen0.size < max) SemispaceCache(gen0 + (k -> v), gen1, max, possiblyEvicted)                   // room in gen0, done!
    else                 SemispaceCache(Map(k -> v), gen0, max, gen1.values.toList ::: possiblyEvicted)// no room in gen0, slide it down

  def lookup(k: K): Option[(SemispaceCache[K, V], V)] =
    gen0.get(k).tupleLeft(this) orElse       // key is in gen0, done!
    gen1.get(k).map(v => (insert(k, v), v))  // key is in gen1, copy to gen0

  def containsKey(k: K): Boolean =
    gen0.contains(k) || gen1.contains(k)

  def values: List[V] =
    (gen0.values.toSet | gen1.values.toSet).toList

  def evictAll: SemispaceCache[K, V] =
    SemispaceCache(Map.empty, Map.empty, max, (gen0.values.toSet | gen1.values.toSet | possiblyEvicted.toSet).toList)

  def clearEvicted: (SemispaceCache[K, V], List[V]) =
    (SemispaceCache(gen0, gen1, max, Nil), (possiblyEvicted.toSet -- values.toSet).toList)
}

object SemispaceCache {

  private def apply[K, V](gen0: Map[K, V], gen1: Map[K, V], max: Int, evicted: List[V]): SemispaceCache[K, V] =
    new SemispaceCache[K, V](gen0, gen1, max, evicted) {}

  def empty[K, V](max: Int): SemispaceCache[K, V] =
    SemispaceCache[K, V](Map.empty, Map.empty, max max 1, Nil)
}
