// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.implicits._
import java.nio.charset.StandardCharsets.UTF_8
import scodec._
import scodec.codecs._
import scodec.interop.cats._

case class RowData(fields: List[Option[String]]) extends BackendMessage

object RowData {

  final val Tag = 'D'
  final val decoder = int16.flatMap(field.replicateA(_)).map(apply)

  private val field: Decoder[Option[String]] =
    int32.flatMap {
      case -1 => Decoder.point(None)
      case n  => bytes(n).map(bv => Some(new String(bv.toArray, UTF_8)))
    }

}

