package skunk
package proto
package message

import cats.implicits._
import java.nio.charset.StandardCharsets.UTF_8
import scodec._
import scodec.codecs._
import scodec.interop.cats._

case class RowData(fields: List[Option[String]]) extends BackendMessage

object RowData {

  // Byte1('D') - Identifies the message as a data row.
  val Tag = 'D'

  // Int16 - The number of column values that follow (possibly zero).
  // Next, a list of fields (see below)
  val decoder: Decoder[RowData] =
    int16.flatMap(field.replicateA(_)).map(apply)

  // Int32 - The length of the column value, in bytes (this count does not include itself). Can be
  //         zero. As a special case, -1 indicates a NULL column value. No value bytes follow in the
  //         NULL case.
  // Byten - The value of the column, in the format indicated by the associated format code. n is the
  //         above length.
  private val field: Decoder[Option[String]] =
    int32.flatMap {
      case -1 => Decoder.point(None)
      case n  => bytes(n).map(bv => Some(new String(bv.toArray, UTF_8)))
    }

}

