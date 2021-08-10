// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.codecs._
import scodec.Encoder

case class Execute(portal: String, maxRows: Int) extends TaggedFrontendMessage('E') {
  def encodeBody = Execute.encoder.encode(this)
}

object Execute {

  val encoder: Encoder[Execute] =
    (utf8z.asEncoder, int32.asEncoder).contramapN[Execute] { p =>
      (p.portal, p.maxRows)
    }

}