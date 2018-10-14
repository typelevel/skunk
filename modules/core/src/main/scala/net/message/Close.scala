package skunk.net.message

import scodec.codecs._

// Byte1('C')
// Identifies the message as a Close command.

// Int32
// Length of message contents in bytes, including self.

// Byte1
// 'S' to close a prepared statement; or 'P' to close a portal.

// String
// The name of the prepared statement or portal to close (an empty string selects the unnamed prepared statement or portal).


sealed abstract case class Close(variant: Byte, name: String) {
  override def toString = s"Close(${variant.toChar},$name)"
}

object Close {

  def statement(name: String): Close = new Close('S', name) {}
  def portal(name: String): Close = new Close('P', name) {}

  implicit val DescribeFrontendMessage: FrontendMessage[Close] =
    FrontendMessage.tagged('C') {
      (byte ~ utf8z).contramap[Close] { d => d.variant ~ d.name }
    }

}