// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data


import scala.collection.immutable.SortedMap

final case class Cache[K, V] private (
  max: Int,
  entries: Map[K, V]
)(accesses: SortedMap[Long, K],
  accessesInverted: Map[K, Long],
  counter: Long
) {

  private def accessesWithoutKey(k: K): SortedMap[Long, K] =
    accessesInverted.get(k).fold(accesses)(oldCounter => accesses - oldCounter)

  def get(k: K): Option[V] =
    lookup(k).map(_._2)  

  def lookup(k: K): Option[(Cache[K, V], V)] =
    entries.get(k) match {
      case Some(v) =>
        val newAccesses = accessesWithoutKey(k) + (counter -> k)
        val newCache = new Cache(max, entries)(newAccesses, accessesInverted + (k -> counter), counter + 1)
        Some(newCache -> v)
      case None =>
        None
    }

  def put(k: K, v: V): Cache[K, V] =
    insert(k, v)._1

  def insert(k: K, v: V): (Cache[K, V], Option[(K, V)]) =
    if (max <= 0) (this, Some((k, v)))
    else if (entries.size >= max && !containsKey(k)) {
      val (counterToEvict, keyToEvict) = accesses.head
      val newCache = new Cache(max, entries - keyToEvict + (k -> v))(accessesWithoutKey(k) - counterToEvict + (counter -> k), accessesInverted + (k -> counter), counter + 1)
      (newCache, Some((keyToEvict, entries(keyToEvict))))
    } else {
      val newCache = new Cache(max, entries + (k -> v))(accessesWithoutKey(k) + (counter -> k), accessesInverted + (k -> counter), counter + 1)
      (newCache, entries.get(k).filter(_ != v).map(k -> _))
    }

  def containsKey(k: K): Boolean = entries.contains(k)
  def values: Iterable[V] = entries.values

  override def toString: String =
   accesses.map { case (_, k) => s"$k -> ${entries(k)}" }.mkString("Cache(", ", ", ")")
}

object Cache {
  def empty[K, V](max: Int): Cache[K, V] =
    new Cache(max max 0, Map.empty)(SortedMap.empty, Map.empty, 0L)
}


