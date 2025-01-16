// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/**
 * Immutable, least recently used cache.
 *
 * Entries are stored in the `entries` hash map. A numeric stamp is assigned to
 * each entry and stored in the `usages` field, which provides a bidirectional
 * mapping between stamp and key, sorted by stamp. The `entries` and `usages`
 * fields always have the same size.
 *
 * Upon put and get of an entry, a new stamp is assigned and `usages`
 * is updated. Stamps are assigned in ascending order and each stamp is used only once.
 * Hence, the head of `usages` contains the least recently used key.
 */
sealed abstract case class Cache[K, V] private (
  max: Int,
  entries: Map[K, V]
)(usages: SortedBiMap[Long, K],
  stamp: Long
) {
  assert(entries.size == usages.size)

  def size: Int = entries.size
  
  def contains(k: K): Boolean = entries.contains(k)

  /**
   * Gets the value associated with the specified key.
   *
   * Accessing an entry makes it the most recently used entry, hence a new cache
   * is returned with the target entry updated to reflect the recent access.
   */
  def get(k: K): Option[(Cache[K, V], V)] =
    entries.get(k) match {
      case Some(v) =>
        val newUsages = usages + (stamp -> k)
        val newCache = Cache(max, entries, newUsages, stamp + 1)
        Some(newCache -> v)
      case None =>
        None
    }

  /**
   * Returns a new cache with the specified entry added along with the
   * entry that was evicted, if any.
   *
   * The evicted value is defined under two circumstances:
   *  - the cache already contains a different value for the specified key,
   *    in which case the old pairing is returned
   *  - the cache has reeached its max size, in which case the least recently
   *    used value is evicted
   *
   * Note: if the cache contains (k, v), calling `put(k, v)` does NOT result
   * in an eviction, but calling `put(k, v2)` where `v != v2` does.
   */
  def put(k: K, v: V): (Cache[K, V], Option[(K, V)]) =
    if (max <= 0) {
      // max is 0 so immediately evict the new entry 
      (this, Some((k, v)))
    } else if (entries.size >= max && !contains(k)) {
      // at max size already and we need to add a new key, hence we must evict
      // the least recently used entry
      val (lruStamp, lruKey) = usages.head
      val newEntries = entries - lruKey + (k -> v)
      val newUsages = usages - lruStamp + (stamp -> k)
      val newCache = Cache(max, newEntries, newUsages, stamp + 1)
      (newCache, Some(lruKey -> entries(lruKey)))
    } else {
      // not growing past max size at this point, so only need to evict if
      // the new entry is replacing an existing entry with different value
      val newEntries = entries + (k -> v)
      val newUsages = usages + (stamp -> k)
      val newCache = Cache(max, newEntries, newUsages, stamp + 1)
      val evicted = entries.get(k).filter(_ != v).map(k -> _)
      (newCache, evicted)
    }

  def values: Iterable[V] = entries.values

  override def toString: String =
   usages.entries.iterator.map { case (_, k) => s"$k -> ${entries(k)}" }.mkString("Cache(", ", ", ")")
}

object Cache {
  private def apply[K, V](max: Int, entries: Map[K, V], usages: SortedBiMap[Long, K], stamp: Long): Cache[K, V] =
    new Cache(max, entries)(usages, stamp) {}
    
  def empty[K, V](max: Int): Cache[K, V] =
    apply(max max 0, Map.empty, SortedBiMap.empty, 0L)
}


