// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.syntax.all._

/**
 * Cache based on a two-generation GC.
 * Taken from https://twitter.com/pchiusano/status/1260255494519865346
 */
sealed abstract case class SemispaceCache[K, V](gen0: Map[K, V], gen1: Map[K, V], max: Int, evicted: SemispaceCache.EvictionSet[V]) {

  assert(max >= 0)
  assert(gen0.size <= max)
  assert(gen1.size <= max)

  def insert(k: K, v: V): SemispaceCache[K, V] = 
    if (max == 0)        SemispaceCache(gen0, gen1, max, evicted + v)                       // immediately evict
    else if (gen0.size < max) SemispaceCache(gen0 + (k -> v), gen1 - k, max, evicted - v)   // room in gen0, remove from gen1, done!
    else                 SemispaceCache(Map(k -> v), gen0, max, evicted ++ gen1.values - v) // no room in gen0, slide it down

  def lookup(k: K): Option[(SemispaceCache[K, V], V)] = 
    gen0.get(k).tupleLeft(this) orElse       // key is in gen0, done!
    gen1.get(k).map(v => (insert(k, v), v))  // key is in gen1, copy to gen0

  def containsKey(k: K): Boolean =
    gen0.contains(k) || gen1.contains(k)

  def values: List[V] =
    (gen0.values.toSet | gen1.values.toSet).toList

  def evictAll: SemispaceCache[K, V] =
    SemispaceCache(Map.empty, Map.empty, max, evicted ++ gen0.values ++ gen1.values)

  def clearEvicted: (SemispaceCache[K, V], List[V]) =
    (SemispaceCache(gen0, gen1, max, evicted.clear), evicted.toList)
}

object SemispaceCache {

  private def apply[K, V](gen0: Map[K, V], gen1: Map[K, V], max: Int, evicted: EvictionSet[V]): SemispaceCache[K, V] = {
    val r = new SemispaceCache[K, V](gen0, gen1, max, evicted) {}
    val gen0Intersection: Set[V] = (gen0.values.toSet intersect evicted.toList.toSet)
    val gen1Intersection: Set[V] = (gen1.values.toSet intersect evicted.toList.toSet)
    assert(gen0Intersection.isEmpty, s"gen0 has overlapping values in evicted: ${gen0Intersection}")
    assert(gen1Intersection.isEmpty, s"gen1 has overlapping values in evicted: ${gen1Intersection}")
    r
  }

  def empty[K, V](max: Int, trackEviction: Boolean): SemispaceCache[K, V] =
    SemispaceCache[K, V](Map.empty, Map.empty, max max 0, if (trackEviction) EvictionSet.empty else new EvictionSet.ZeroEvictionSet)

  sealed trait EvictionSet[V] {
    def +(v: V): EvictionSet[V]
    def ++(vs: Iterable[V]): EvictionSet[V]
    def -(v: V): EvictionSet[V]
    def toList: List[V]
    def clear: EvictionSet[V]
  }

  private[SemispaceCache] object EvictionSet {

    class ZeroEvictionSet[V] extends EvictionSet[V] {
      def +(v: V): EvictionSet[V] = this
      def ++(vs: Iterable[V]): EvictionSet[V] = this
      def -(v: V): EvictionSet[V] = this
      def toList: List[V] = Nil
      def clear: EvictionSet[V] = this
    }

    class DefaultEvictionSet[V](underlying: Set[V]) extends EvictionSet[V] {
      def +(v: V): EvictionSet[V] = new DefaultEvictionSet(underlying + v)
      def ++(vs: Iterable[V]): EvictionSet[V] = new DefaultEvictionSet(underlying ++ vs)
      def -(v: V): EvictionSet[V] = new DefaultEvictionSet(underlying - v)
      def toList: List[V] = underlying.toList
      def clear: EvictionSet[V] = new DefaultEvictionSet(Set.empty)
    }

    def empty[V]: EvictionSet[V] = new DefaultEvictionSet(Set.empty)
  }
}
