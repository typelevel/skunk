// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.postgis
package ewkb

import scodec.Attempt
import scodec.{Codec => Scodec}
import scodec.bits.BitVector
import scodec.bits.ByteOrdering

trait EWKBCodecPlatform extends EWKBPrimitives {
  def ewkbEncode(ewkb: EWKBType, geometry: Geometry, geoEncoder: Scodec[Geometry])(implicit bo: ByteOrdering): Attempt[BitVector] = {
    val encoder = byteOrdering :: ewkbType :: geoEncoder
    encoder.encode(bo :: ewkb :: geometry :: shapeless.HNil)
  }
}
