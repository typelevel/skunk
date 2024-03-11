// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.codecs._
import scodec.Encoder

sealed abstract case class Describe(variant: Byte, name: String) extends TaggedFrontendMessage('D') {
  override def toString = s"Describe(${variant.toChar}, $name)"
  def encodeBody = Describe.encoder.encode(this)
}

object Describe {

  def statement(name: String): Describe =
    new Describe('S', name) {}

  def portal(name: String): Describe =
    new Describe('P', name) {}

  val encoder: Encoder[Describe] =
    (byte.asEncoder, utf8z.asEncoder).contramapN[Describe] { d => (d.variant, d.name) }

}