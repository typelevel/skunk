// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/**
 * Immutable, least recently used cache.
 *
 * Entries are stored in a hash map. Upon insertion and upon each access of an entry,
 * a numeric stamp is assigned to each entry. Stamps start at 0 and increase for each
 * insertion/access. The `accesses` field stores a sorted map of stamp to entry key.
 * Resultantly, the head of `accesses` is the key of the least recently used entry.
 */
sealed abstract case class Cache[K, V] private (
  max: Int,
  entries: Map[K, V]
)(accesses: SortedBiMap[Long, K],
  stamp: Long
) {
  assert(entries.size == accesses.size)

  def size: Int = entries.size
  
  def contains(k: K): Boolean = entries.contains(k)

  def get(k: K): Option[(Cache[K, V], V)] =
    entries.get(k) match {
      case Some(v) =>
        val newAccesses = accesses + (stamp -> k)
        val newCache = Cache(max, entries, newAccesses, stamp + 1)
        Some(newCache -> v)
      case None =>
        None
    }

  def put(k: K, v: V): (Cache[K, V], Option[(K, V)]) =
    if (max <= 0) (this, Some((k, v)))
    else if (entries.size >= max && !contains(k)) {
      val (stampToEvict, keyToEvict) = accesses.head
      val newEntries = entries - keyToEvict + (k -> v)
      val newAccesses = accesses - stampToEvict + (stamp -> k)
      val newCache = Cache(max, newEntries, newAccesses, stamp + 1)
      (newCache, Some((keyToEvict, entries(keyToEvict))))
    } else {
      val newEntries = entries + (k -> v)
      val newAccesses = accesses + (stamp -> k)
      val newCache = Cache(max, newEntries, newAccesses, stamp + 1)
      // If the new value is different than what was previously stored
      // under this key, if anything, evict the old (k, v) pairing
      val evicted = entries.get(k).filter(_ != v).map(k -> _)
      (newCache, evicted)
    }

  def values: Iterable[V] = entries.values

  override def toString: String =
   accesses.entries.iterator.map { case (_, k) => s"$k -> ${entries(k)}" }.mkString("Cache(", ", ", ")")
}

object Cache {
  private def apply[K, V](max: Int, entries: Map[K, V], accesses: SortedBiMap[Long, K], stamp: Long): Cache[K, V] =
    new Cache(max, entries)(accesses, stamp) {}
    
  def empty[K, V](max: Int): Cache[K, V] =
    apply(max max 0, Map.empty, SortedBiMap.empty, 0L)
}


