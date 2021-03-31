// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.codecs._
import scodec.Encoder

case class Parse(name: String, sql: String, types: List[Int]) extends TaggedFrontendMessage('P') {
  def encodeBody = Parse.encoder.encode(this)
}

object Parse {

  val encoder: Encoder[Parse] =
    (utf8z.asEncoder, utf8z.asEncoder, int16.asEncoder, list(int32).asEncoder).contramapN[Parse] { p =>
      (p.name, p.sql, p.types.length, p.types)
    }

}