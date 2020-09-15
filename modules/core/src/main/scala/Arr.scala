// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import scala.collection.mutable.ArrayBuffer
import cats.syntax.all._
import cats.Foldable
import cats.kernel.Eq

final class Arr[A] private[skunk] (
  private[skunk] val data:   ArrayBuffer[A],
  private[skunk] val extent: Array[Int]
) {

  // N.B. this is not the kind of programming I like to do, but this needs to be efficient so we're
  // trying pretty hard not to allocate unless we absolutely have to.

  // data and extent must be consistent
  assert(
    (data.isEmpty && extent.isEmpty) ||
    (data.length  == extent.product)
  )

  // We need the offsets associated with each dimension
  private val _offsets: Array[Int] =
    extent.tails.map(_.product).drop(1).toArray

  /**
   * Encode this `Arr` into a Postgres array literal, using `f` to encode/escape the values, and
   * the given delimiter (almost always a comma).
   */
  def encode(f: A => String, delim: Char = ','): String = {
    def go(offset: Int, ie: Int, sb: StringBuilder): Unit = {
      val v = ie == extent.length - 1 // append values?
      val o = _offsets(ie)
      var i = 0
      while (i < extent(ie)) {
        if (i > 0) sb.append(delim)
        if (v) {
          sb.append(f(data(offset + i)))
        } else {
          sb.append('{')
          go(offset + o * i, ie + 1, sb)
          sb.append('}')
        }
        i += 1
      }
    }

    if (extent.isEmpty) "{}"
    else {
      val sb = new StringBuilder
      sb.append('{')
      go(0, 0, sb)
      sb.append('}')
      sb.toString()
    }
  }

  // public API

  def reshape(extent: Int*): Option[Arr[A]] =
    if (extent.isEmpty) {
      if (this.extent.isEmpty) Some(this)
      else None
    } else {
      if (extent.product == data.length) Some(new Arr(data, extent.toArray))
      else None
    }

  def map[B](f: A => B): Arr[B] =
    new Arr(data.map(f), extent)

  def emap[B](f: A => Either[String, B]): Either[String, Arr[B]] = {
    val _newData = ArrayBuffer.empty[B]
    data.foreach { a =>
      f(a) match {
        case Right(b) => _newData.append(b)
        case Left(s)  => return Left(s)
      }
    }
    Right(new Arr(_newData, extent))
  }


  val size: Int =
    data.length

  def dimensions: List[Int] =
    extent.toList

  def get(is: Int*): Option[A] =
    if (is.length == extent.length) {
      var a = 0
      var i = 0
      while (i < extent.length) {
        val ii = is(i)
        if (ii >= 0 && ii < extent(i)) {
          a += is(i) * _offsets(i)
        } else {
          return None
        }
        i += 1
      }
      Some(data(a))
    } else None

  override def toString =
    encode(_.toString)

}

object Arr {

  def apply[A](as: A*): Arr[A] =
    fromFoldable(as.toList)

  def empty[A]: Arr[A] =
    new Arr(ArrayBuffer.empty, Array.empty)

  def fromFoldable[F[_]: Foldable, A](fa: F[A]): Arr[A] = {
    val data = fa.foldLeft(new ArrayBuffer[A]) { (a, b) => a.append(b); a }
    new Arr(data, Array(data.length))
  }

  /**
   * `Arr`s are comparable if their elements are comparable. They are equal if they have the same
   * elements and dimensions.
   */
  implicit def eqArr[A: Eq]: Eq[Arr[A]] = (a, b) =>
    (a.extent.corresponds(b.extent)(_ === _)) &&
    (a.data.corresponds(b.data)(_ === _))

}