package skunk.net.message

import cats.implicits._
import scodec._
import scodec.codecs._
import scodec.interop.cats._
import skunk.data.Type

case class RowDescription(fields: List[RowDescription.Field]) extends BackendMessage {
  def oids: List[Int] = fields.map(_.typeOid)
  override def toString = s"RowDescription(${fields.mkString("; ")})"
}

object RowDescription {

  // Byte1('T') - Identifies the message as a row description.
  val Tag = 'T'

  // Int16 - Specifies the number of fields in a row (can be zero).
  // Then, a list of fields (see `Field` below).
  val decoder: Decoder[RowDescription] =
    int16.flatMap { n =>
      Field.decoder.replicateA(n).map(RowDescription(_))
    }

  final case class Field(name: String, tableOid: Int, columnAttr: Int, typeOid: Int, typeSize: Int, typeMod: Int, format: Int /* always 0 */) {
    override def toString = {
      val t = Type.forOid(typeOid).getOrElse(oid => s"Unknown($oid)")
      val s = if (typeSize >= 0) s"$typeSize bytes" else s"*"
      val f = format match { case 0 => "text" ; case 1 => "binary" }
      s"Field($name, $tableOid, $columnAttr, $t, $s, $typeMod, $f)"
    }
  }
  object Field {

    // String - The field name.
    // Int32  - If the field can be identified as a column of a specific table, the object ID of the
    //          table; otherwise zero.
    // Int16  - If the field can be identified as a column of a specific table, the attribute number
    //          of the column; otherwise zero.
    // Int32  - The object ID of the field's data type.
    // Int16  - The data type size (see pg_type.typlen). Note that negative values denote
    //          variable-width types.
    // Int32  - The type modifier (see pg_attribute.atttypmod). The meaning of the modifier is
    //          type-specific.
    // Int16  - The format code being used for the field. Currently will be zero (text) or one
    //          (binary). In a RowDescription returned from the statement variant of Describe, the
    //          format code is not yet known and will always be zero.
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