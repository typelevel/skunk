// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import java.nio.charset.StandardCharsets.UTF_8
import scodec._
import scodec.codecs._
import scodec.bits.BitVector

case class RowData(fields: List[Option[String]]) extends BackendMessage

object RowData {

  final val Tag = 'D'
  final val decoder: Decoder[RowData] =
    /* Faster version of the following (with less error handling):
     * val field = int32.flatMap {
     *   case -1 => none[String].pure[Decoder]
     *   case n => bytes(n).map(bv => Some(new String(bv.toArray, UTF_8)))
     * }
     * codecs.listOfN(int16, field).map(apply)
     */
    new Decoder[RowData] {
      def decode(bits: BitVector): Attempt[DecodeResult[RowData]] = {
        var remainder = bits
        val fieldBuilder = List.newBuilder[Option[String]]
        var remainingFields = int16.decodeValue(remainder).require
        remainder = remainder.drop(16)
        while (remainingFields > 0) {
          val (fieldSizeBits, postFieldSize) = remainder.splitAt(32)
          val fieldSizeNumBytes = fieldSizeBits.toInt()
          if (fieldSizeNumBytes == -1) {
            fieldBuilder += None
            remainder = postFieldSize
          } else {
            val (fieldBits, postFieldBits) = postFieldSize.splitAt(fieldSizeNumBytes * 8L)
            fieldBuilder += Some(new String(fieldBits.toByteArray, UTF_8))
            remainder = postFieldBits
          }
          remainingFields -= 1
        }
        Attempt.Successful(DecodeResult(RowData(fieldBuilder.result()), remainder))
      }
    }
}

