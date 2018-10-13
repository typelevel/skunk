package skunk.message

import scodec.codecs._

// Describe (F)
// Byte1('D')
// Identifies the message as a Describe command.

// Int32
// Length of message contents in bytes, including self.

// Byte1
// 'S' to describe a prepared statement; or 'P' to describe a portal.

// String
// The name of the prepared statement or portal to describe (an empty string selects the unnamed prepared statement or portal).


sealed abstract case class Describe(variant: Byte, name: String) {
  override def toString =
    s"Describe(${variant.toChar}, $name)"
}

object Describe {

  def statement(name: String): Describe = new Describe('S', name) {}
  def portal(name: String): Describe = new Describe('P', name) {}

  implicit val DescribeFrontendMessage: FrontendMessage[Describe] =
    FrontendMessage.tagged('D') {
      (byte ~ utf8z).contramap[Describe] { d => d.variant ~ d.name }
    }

}