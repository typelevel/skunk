// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs._
import scodec.Encoder

sealed abstract case class Close(variant: Byte, name: String) extends TaggedFrontendMessage('C') {
  override def toString: String = s"Close(${variant.toChar},$name)"
  def encodeBody = Close.encoder.encode(this)
}

object Close {

  def statement(name: String): Close =
    new Close('S', name) {}

  def portal(name: String): Close =
    new Close('P', name) {}

  val encoder: Encoder[Close] =
    (byte ~ utf8z).contramap[Close] { d =>
      d.variant ~ d.name
    }

}