package skunk.net.message

import scodec.codecs._

sealed abstract case class Close(variant: Byte, name: String) {
  override def toString = s"Close(${variant.toChar},$name)"
}

object Close {

  def statement(name: String): Close =
    new Close('S', name) {}

  def portal(name: String): Close =
    new Close('P', name) {}

  implicit val DescribeFrontendMessage: FrontendMessage[Close] =
    FrontendMessage.tagged('C') {
      (byte ~ utf8z).contramap[Close] { d =>
        d.variant ~ d.name
      }
    }

}