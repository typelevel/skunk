// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.implicits._
import scodec._
import scodec.codecs._
import scodec.interop.cats._

case class RowDescription(fields: List[RowDescription.Field]) extends BackendMessage {
  override def toString = s"RowDescription(${fields.mkString("; ")})"
}

object RowDescription {

  final val Tag = 'T'

  val decoder: Decoder[RowDescription] =
    int16.flatMap { n =>
      Field.decoder.replicateA(n).map(RowDescription(_))
    }

  final case class Field(name: String, tableOid: Int, columnAttr: Int, typeOid: Int, typeSize: Int, typeMod: Int, format: Int /* always 0 */) {
    override def toString =
      s"Field($name, $typeOid)"
  }

  object Field {

    val decoder: Decoder[Field] =
      (cstring ~ int32 ~ int16 ~ int32 ~ int16 ~ int32 ~ int16).map(apply)

      // // numeric precision from typeMod
      // @ ((655368 - 4) >> 16) & 65535
      // res3: Int = 10
      // // numeric scale
      // @ (655368 - 4) & 65535
      // res4: Int = 4

      // for bpchar and varchar the size is typeMod - 4

  }

}