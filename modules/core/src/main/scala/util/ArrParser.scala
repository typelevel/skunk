package skunk.util

import scala.collection.mutable.ArrayBuffer
import skunk.Arr

object ArrParser {

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
        println(s"$state, $depth, $dataDepth, $index, $c")
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