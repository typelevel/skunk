package skunk

package object exception {

  private[exception] def framed(s: String) =
    "\u001B[4m" + s + "\u001B[24m"

}