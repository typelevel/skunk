// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import java.nio.charset.StandardCharsets.UTF_8
import scodec._
import scodec.codecs._

case class RowData(fields: List[Option[String]]) extends BackendMessage

object RowData {

  private val field: Codec[Option[String]] =
    int32.flatMap {
      case -1 => none[String].pure[Decoder]
      case n  => bytes(n).map(bv => Some(new String(bv.toArray, UTF_8)))
    }.decodeOnly

  final val Tag = 'D'
  final val decoder: Decoder[RowData] =
    codecs.listOfN(int16, field).map(apply)

}

