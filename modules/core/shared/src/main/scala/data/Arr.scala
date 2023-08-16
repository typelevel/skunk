// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats._
import cats.syntax.all._
import scala.collection.Factory
import scala.collection.mutable.ArrayBuffer

/**
 * A Postgres array, which is either empty and zero-dimensional, or non-empty and rectangular (unlike
 * Scala `Array`s, which are ragged) with a postive number of dimensions (all non-empty). `Arr` is a
 * traversable functor.
 */
final class Arr[A] private (
  protected val data:   ArrayBuffer[A],
  private   val extent: Array[Int]
) {

  // Data and extent must be consistent. Should be guaranteed but let's check anyway.
  assert((data.isEmpty && extent.isEmpty) || (data.length  == wrapIntArray(extent).product))

  // In order to access elements we need the offsets associated with each dimension. But let's not
  // compute it unless we need to since these will be constructed in a tight loop when we unroll
  // a resultset that contains array columns.
  private lazy val _offsets: Array[Int] =
    extent.tails.map(wrapIntArray(_).product).drop(1).toArray

  /**
   * Attempt to reshape this `Arr` with the specified dimensions. This is possible if and only if
   * dimensions factorize `size`. O(1).
   * @group Transformation
   */
  def reshape(dimensions: Int*): Option[Arr[A]] =
    if (dimensions.isEmpty) {
      if (this.dimensions.isEmpty) Some(this)
      else None
    } else {
      if (dimensions.product == data.length) Some(new Arr(data, dimensions.toArray))
      else None
    }

  /**
   * Total number of elements in this `Arr`.
   * @group Accessors
   */
  def size: Int =
    data.length

  /** True if this `Arr` is empty. Invariant: `isEmpty == dimensions.isEmpty`. */
  def isEmpty: Boolean =
    data.isEmpty

  /**
   * Size of this `Arr` by dimension. Invariant: if this `Arr` is non-empty then
   * `dimensions.product` equals `size`.
   * @group Accessors
   */
  def dimensions: List[Int] =
    extent.toList

  /**
   * Construct this `Arr`'s elements as a collection `C`, as if first reshaped to be
   * single-dimensional.
   */
  def flattenTo[C](fact: Factory[A, C]): C =
    data.to(fact)

  /**
   * Retrieve the element at the specified location, if the ordinates are in range (i.e., between
   * zero and the corresponding entry in `dimensions`).
   * @group Accessors
   */
  def get(ords: Int*): Option[A] =
    if (ords.length == extent.length) {
      var a = 0
      var i = 0
      while (i < extent.length) {
        val ii = ords(i)
        if (ii >= 0 && ii < extent(i)) {
          a += ords(i) * _offsets(i)
        } else {
          return None
        }
        i += 1
      }
      Some(data(a))
    } else None

  /**
   * Encode this `Arr` into a Postgres array literal, using `f` to encode the values (which will
   * be quoted and escaped as needed) and the given delimiter (specified in `pg_type` but almost
   * always a comma).
   * @group Encoding
   */
  def encode(f: A => String, delim: Char = ','): String = {

    // We quote and escape all data, even if it's not strictly necessary. We could optimize here
    // and only quote if needed (see https://www.postgresql.org/docs/9.6/arrays.html#ARRAYS-IO).
    def appendEscaped(sb: StringBuilder, s: String): Unit = {
      sb.append('"')
      s.foreach {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case c    => sb.append(c)
      }
      sb.append('"')
      ()
    }

    // The main loop.
    def go(offset: Int, ie: Int, sb: StringBuilder): Unit = {
      val v = ie == extent.length - 1
      val o = _offsets(ie)
      var i = 0
      while (i < extent(ie)) {
        if (i > 0) sb.append(delim)
        if (v) {
          appendEscaped(sb, f(data(offset + i)))
        } else {
          sb.append('{')
          go(offset + o * i, ie + 1, sb)
          sb.append('}')
        }
        i += 1
      }
    }

    // And the main event.
    if (extent.isEmpty) "{}"
    else {
      val sb = new StringBuilder
      sb.append('{')
      go(0, 0, sb)
      sb.append('}')
      sb.toString()
    }

  }

  /** Approximation of the equivalent Postgres array literal, for debugging only. */
  override def toString =
    s"Arr(${encode(_.toString)})"

}

/** Companion object for `Arr`, with constructors and methods for parsing. */
object Arr {

  /**
   * Construct a one-dimensional `Arr` from the given values (call `reshape` after construction to
   * change dimensionality).
   * @group Constructors
   */
  def apply[A](as: A*): Arr[A] =
    fromFoldable(as.toList)

  /**
   * Construct an empty `Arr`.
   * @group Constructors
   */
  def empty[A]: Arr[A] =
    new Arr(ArrayBuffer.empty, Array.empty)

  /**
   * Construct a one-dimensional `Arr` from the given foldable (call `reshape` after construction to
   * change dimensionality).
   * @group Constructors
   */
  def fromFoldable[F[_]: Foldable, A](fa: F[A]): Arr[A] = {
    val data = fa.foldLeft(new ArrayBuffer[A]) { (a, b) => a.append(b); a }
    if (data.isEmpty) Arr.empty[A] else new Arr(data, Array(data.length))
  }

  /**
   * `Arr`s are comparable if their elements are comparable. They are equal if they have the same
   * elements and dimensions.
   * @group Typeclass Instances
   */
  implicit def eqArr[A: Eq]: Eq[Arr[A]] = (a, b) =>
    (a.extent.corresponds(b.extent)(_ === _)) &&
    (a.data.corresponds(b.data)(_ === _))

  /**
   * `Arr` is a traversable functor.
   * @group Typeclass Instances
   */
  implicit val TraverseArr: Traverse[Arr] =
    new Traverse[Arr] {

      def foldLeft[A, B](fa: Arr[A], b: B)(f: (B, A) => B): B =
        fa.data.foldLeft(b)(f)

      def foldRight[A, B](fa: Arr[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
        fa.data.toList.foldr(lb)(f)

      def traverse[G[_]: Applicative, A, B](fa: Arr[A])(f: A => G[B]): G[Arr[B]] =
        fa.data.toList.traverse(f).map(bs => new Arr(ArrayBuffer(bs:_*), fa.extent))

      override def map[A, B](fa: Arr[A])(f: A => B): Arr[B] =
        new Arr(fa.data.map(f), fa.extent)

    }


  // The rest of this source file is the parser.

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

  /**
   * Parse a Postgres array literal into an `Arr[String]` or report an error.
   * @group Parsing
   */
  def parse(s: String): Either[String, Arr[String]] =
    parseWith(Right(_))(s)

  /**
   * Parse a Postgres array literal into an `Arr[A]` or report an error.
   * @group Parsing
   */
  def parseWith[A](f: String => Either[String, A])(s: String): Either[String, Arr[A]] =
    if (s == "{}") Right(Arr.empty) // This really is a special case!
    else {

      // The number of leading braces tells us the depth at which data lives.
      val dataDepth = s.takeWhile(_ == '{').length()

      // We accumulate a buffer of data
      val data = ArrayBuffer.empty[A]

      // We reuse a StringBuilder for each datum we encounter.
      val datum = new StringBuilder

      // We track our current index and our depth, as well as the current and "reference" counts of
      // elements at each depth. These allow us to ensure that the array is rectangular.
      var index     = 0
      var depth     = 0
      val curCount  = Array.fill(dataDepth + 1)(0) // +1 so we can say curCount(depth)
      val refCount  = Array.fill(dataDepth + 1)(0) // same here

      // We have a failure sentinal and a helper to set it.
      var failure: String = null
      def fail(msg: String): Unit =
        failure = s"parse error at index $index: $msg"

      // This is a state machine!
      var state: ParseState = ExpectArray

      // After we encounter a comma we update our current count of elements at the current depth.
      // If there is a reference count for that depth we can trap the case where we have too many.
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

      // After we encounter a closing brace we set our reference count, if it's not already set. We
      // also catch the case where the current count is too low because more elements were expected.
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

      // Ok here is our main loop. We run as long as there are more chars in the input and we
      // haven't recorded an error.
      while (index < s.length && failure == null) {
        val c = s(index)
        state match {

          // After we encounter a closing brace that returns us to depth = 0.
          case Done =>
            fail(s"expected end of string, found $c")

          // After a comma or an opening brace.
          case ExpectElem =>
            state = if (depth == dataDepth) ExpectDatum else ExpectArray

          // Array expected, so we must find an opening brace and nothing else.
          case ExpectArray =>
            c match {

              case '{' =>
                index += 1
                depth += 1
                state = ExpectElem

              case _ =>
                fail(s"expected '{', found $c")

            }

          // Datum expected, so we expect an opening quote or a non-syntax char.
          case ExpectDatum =>
            c match {

              case '{' | '}' | ',' | '\\' =>
                fail(s"expected datum, found '$c'")

              case '"' =>
                index += 1
                datum.clear()
                state = InDatumQuoted

              case  _  =>
                index += 1
                datum.clear()
                datum.append(c)
                state = InDatumUnquoted

            }

          // Inside a quoted datum we expect a closing quote, an escape, or an arbitrary char.
          case InDatumQuoted =>
            c match {

              case '"' =>
                f(datum.toString()).fold(fail, a => { data.append(a); () })
                index += 1
                state = ElemComplete

              case '\\' =>
                index += 1
                state = InEscape

              case c =>
                datum.append(c)
                index += 1

            }

          // Inside a quoted datum we expect a closing comma/brace, or a non-syntax char.
          case InDatumUnquoted =>
            c match {

              case '{' | '\\' =>
                fail(s"illegal character in unquoted datum: '$c'")

              case ',' =>
                if (datum.toString().equalsIgnoreCase("null")) fail(s"encountered NULL array element (currently unsupported)")
                updateCountersAfterComma()
                f(datum.toString()).fold(fail, a => { data.append(a); () })
                index += 1
                state = ExpectDatum

              case '}' =>
                if (datum.toString().equalsIgnoreCase("null")) fail(s"encountered NULL array element (currently unsupported)")
                updateCountersAfterClose()
                f(datum.toString()).fold(fail, a => { data.append(a); () })
                index += 1
                depth -= 1
                state = if (depth == 0) Done else ElemComplete

              case _ =>
                datum.append(c)
                index += 1

            }

          // Following an escape (inside a quoted datum) we accept all chars.
          case InEscape =>
            datum.append(c)
            index += 1
            state = InDatumQuoted

          // Following a closing quote we expect a comma or closing brace.
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

      // And we're done!
      if (failure != null)
        Left(failure)
      else if (depth != 0 || state != Done)
        Left(s"unterminated array literal")
      else
        Right(new Arr(data, refCount.drop(1))) // because we use 1-based indexing above

    }

}