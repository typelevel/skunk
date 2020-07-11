// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.codecs._
import scodec.Encoder

case class Parse(name: String, sql: String, types: List[Int]) extends TaggedFrontendMessage('P') {
  def encodeBody = Parse.encoder.encode(this)
}

object Parse {

  val encoder: Encoder[Parse] =
    (utf8z ~ utf8z ~ int16 ~ list(int32)).contramap[Parse] { p =>
      p.name ~ p.sql ~ p.types.length ~ p.types
    }

}