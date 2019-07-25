// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs._

sealed abstract case class Describe(variant: Byte, name: String) {
  override def toString = s"Describe(${variant.toChar}, $name)"
}

object Describe {

  def statement(name: String): Describe =
    new Describe('S', name) {}

  def portal(name: String): Describe =
    new Describe('P', name) {}

  implicit val DescribeFrontendMessage: FrontendMessage[Describe] =
    FrontendMessage.tagged('D') {
      (byte ~ utf8z).contramap[Describe] { d =>
        d.variant ~ d.name
      }
    }

}
