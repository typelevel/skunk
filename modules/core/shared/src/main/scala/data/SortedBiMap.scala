// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import scala.collection.immutable.SortedMap
import scala.math.Ordering

/** Immutable bi-directional map that is sorted by key. */
sealed abstract case class SortedBiMap[K: Ordering, V](entries: SortedMap[K, V], inverse: Map[V, K]) {
  assert(entries.size == inverse.size)

  def size: Int = entries.size

  def head: (K, V) = entries.head

  def get(k: K): Option[V] = entries.get(k)

  def put(k: K, v: V): SortedBiMap[K, V] =
    SortedBiMap(
      inverse.get(v).fold(entries)(entries - _) + (k -> v), 
      entries.get(k).fold(inverse)(inverse - _) + (v -> k))

  def +(kv: (K, V)): SortedBiMap[K, V] = put(kv._1, kv._2)

  def -(k: K): SortedBiMap[K, V] =
    get(k) match {
      case Some(v) => SortedBiMap(entries - k, inverse - v)
      case None => this
    }

  def inverseGet(v: V): Option[K] = inverse.get(v)

  override def toString: String =
    entries.iterator.map { case (k, v) => s"$k <-> $v" }.mkString("SortedBiMap(", ", ", ")")
}

object SortedBiMap {
  private def apply[K: Ordering, V](entries: SortedMap[K, V], inverse: Map[V, K]): SortedBiMap[K, V] =
    new SortedBiMap[K, V](entries, inverse) {}

  def empty[K: Ordering, V]: SortedBiMap[K, V] = apply(SortedMap.empty, Map.empty)
}

