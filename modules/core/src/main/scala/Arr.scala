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

  private def escape(s: String): String =
    s.map {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case c    => c
    }.mkString("\"", "", "\"")

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
          sb.append(escape(f(data(offset + i))))
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

      // Our parse state
  private sealed trait ParseState
  private case object ExpectElem      extends ParseState
  private case object ExpectArray     extends ParseState
  private case object ExpectDatum     extends ParseState
  private case object InDatumQuoted   extends ParseState
  private case object InDatumUnquoted extends ParseState
  private case object InEscape        extends ParseState
  private case object ElemComplete    extends ParseState
  private case object Done            extends ParseState

  /** Parse a Postgres array literal into an `Arr[String]` or report an error. */
  def parse(s: String): Either[String, Arr[String]] =
    parseWith(Right(_))(s)

  /** Parse a Postgres array literal into an `Arr[A]` or report an error. */
  def parseWith[A](f: String => Either[String, A])(s: String): Either[String, Arr[A]] = {

    // This attempts to be an efficient parser, and as such it's gross. Sorry about that. We try
    // really hard not to allocate anything unless we absolutely have to.

    // This really is a special case! If there's no data it has to be "{}" (not "{{}}" etc.)
    if (s == "{}") Right(Arr.empty)
    else {

      // The number of leading braces tells us the depth at which data lives.
      val dataDepth = s.takeWhile(_ == '{').length()

      // We accumulate a buffer of stuff
      val data = ArrayBuffer.empty[A]

      // We reuse a StringBuilder for each datum we encounter, leaving it null if we're not
      // currently parsing a datum.
      val builder = new StringBuilder
      var datum   = null : StringBuilder

      // We track our current index and our depth, as well as the current and "reference" counts of
      // elements at each depth. These allow us to ensure that the array is rectangular.
      var index     = 0
      var depth     = 0
      val curCount  = Array.fill(dataDepth + 1)(0) // +1 so we can say curCount(depth)
      val refCount  = Array.fill(dataDepth + 1)(0)

      // We have a failure sentinal and a helper to set it.
      var failure: String = null
      def fail(msg: String): Unit =
        failure = s"parse error at index $index: $msg"

      // This is a state machine!
      var state: ParseState = ExpectArray

      def updateCountersAfterComma(): Unit = {
        val ref = refCount(depth)
        val cur = curCount(depth)
        val inc = cur + 1
        if (ref > 0 && inc == ref) {
          fail(s"expected $ref element(s) here; found more")
        } else {
          curCount(depth) = inc
        }
      }

      def updateCountersAfterClose(): Unit = {
        val ref = refCount(depth)
        val cur = curCount(depth)
        val inc = cur + 1
        if (ref > 0 && inc < ref) { // it can't be greater
          fail(s"expected $ref element here, only found $inc")
        } else {
          curCount(depth) = 0
          refCount(depth) = inc
        }
      }

      // Our main loop.
      while (index < s.length && failure == null) {
        val c = s(index)
        // println(s"$state, $depth, $dataDepth, $index, $c")
        state match {

          case Done =>
            fail(s"expected end of string, found $c")

          case ExpectElem =>
            state = if (depth == dataDepth) ExpectDatum else ExpectArray

          case ExpectArray =>
            c match {

              case '{' =>
                index += 1
                depth += 1
                state = ExpectElem

              case _ =>
                fail(s"expected '{', found $c")

            }

          case ExpectDatum =>
            c match {

              case '{' | '}' | ',' | '\\' =>
                fail(s"expected datum, found '$c'")

              case '"' =>
                index += 1
                datum = builder
                datum.clear()
                state = InDatumQuoted

              case  _  =>
                index += 1
                datum = builder
                datum.clear()
                datum.append(c)
                state = InDatumUnquoted
            }

          case InDatumQuoted =>
            c match {

              case '"' =>
                f(datum.toString()).fold(fail, a => { data.append(a); () })
                datum = null
                index += 1
                state = ElemComplete

              case '\\' =>
                index += 1
                state = InEscape

              case c =>
                datum.append(c)
                index += 1

            }

          case InDatumUnquoted =>
            c match {

              case '{' | '\\' =>
                fail(s"illegal character in unquoted datum: '$c'")

              case ',' =>
                updateCountersAfterComma()
                f(datum.toString()).fold(fail, a => { data.append(a); () })
                datum = null
                index += 1
                state = ExpectDatum

              case '}' =>
                updateCountersAfterClose()
                f(datum.toString()).fold(fail, a => { data.append(a); () })
                datum = null
                index += 1
                depth -= 1
                state = if (depth == 0) Done else ElemComplete

              case _ =>
                datum.append(c)
                index += 1

            }

          case InEscape =>
            c match {
              case 'b' => datum.append('\b')
              case 'f' => datum.append('\f')
              case 'n' => datum.append('\n')
              case 'r' => datum.append('\r')
              case 't' => datum.append('\t')
              case 'o' => fail("octal escapes are not yet supported.")
              case 'x' => fail("hexadecimal escapes are not yet supported.")
              case 'u' => fail("unicode escapes are not yet supported.")
              case  c => datum.append(c)
            }
            index += 1
            state = InDatumQuoted

          case ElemComplete =>
            c match {

              case ',' =>
                updateCountersAfterComma()
                index += 1
                state = ExpectElem

              case '}' =>
                updateCountersAfterClose()
                index += 1
                depth -= 1
                if (depth == 0)
                  state = Done

              case _   =>
                fail(s"expected ',' or '}', found $c")

            }

        }

      }

      if (failure != null)
        Left(failure)
      else if (depth != 0 || state != Done)
        Left(s"unterminated array literal")
      else
        Right(new Arr(data, refCount.drop(1)))

    }
  }

}