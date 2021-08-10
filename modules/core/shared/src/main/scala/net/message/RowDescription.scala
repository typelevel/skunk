// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec._
import scodec.codecs._
import scodec.interop.cats._
import skunk.data.TypedRowDescription
import skunk.util.Typer

case class RowDescription(fields: List[RowDescription.Field]) extends BackendMessage {
  override def toString: String = s"RowDescription(${fields.mkString("; ")})"

  /**
   * Attempt to type each field, returning a `TypedRowDescription` on success or a list of aligned
   * pairs on failure (one or more will be `None`) for error reporting.
   */
  def typed(ty: Typer): Either[List[(RowDescription.Field, Option[TypedRowDescription.Field])], TypedRowDescription] = {
    val otfs = fields.map(f => ty.typeForOid(f.typeOid, f.typeMod).map(TypedRowDescription.Field(f.name, _)))
    otfs.sequence match {
      case Some(tfs) => TypedRowDescription(tfs).asRight
      case None      => fields.zip(otfs).asLeft
    }
  }

}

object RowDescription {

  final val Tag = 'T'

  val decoder: Decoder[RowDescription] =
    codecs.listOfN(int16, Field.codec).map(RowDescription(_))

  final case class Field(name: String, tableOid: Int, columnAttr: Int, typeOid: Int, typeSize: Int, typeMod: Int, format: Int /* always 0 */) {
    override def toString: String = s"Field($name, $typeOid)"
  }

  object Field {

    val codec: Codec[Field] =
      (utf8z.asDecoder, int32.asDecoder, int16.asDecoder, int32.asDecoder, int16.asDecoder, int32.asDecoder, int16.asDecoder)
        .mapN(apply)
        .decodeOnly

  }

}
