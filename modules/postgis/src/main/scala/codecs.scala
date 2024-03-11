// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package postgis
package codecs

import scala.reflect.ClassTag

import cats.syntax.all._
import scodec.bits.ByteVector
import skunk.data.Type

trait PostGISGeometryCodecs {

  val geometry: Codec[Geometry] = Codec.simple[Geometry](
    geometry => ewkb.codecs.geometry.encode(geometry).require.toHex,
    str => {
      ByteVector.fromHex(str).fold(s"[postgis] Bad EWKB Hex: $str".asLeft[Geometry]) { byteVector =>
        ewkb.codecs.geometry.decodeValue(byteVector.toBitVector).toEither.leftMap(_.message)
      }
    },
    Type("geometry")
  )

  val point: Codec[Point]                           = geometryCodec[Point]
  val lineString: Codec[LineString]                 = geometryCodec[LineString]
  val polygon: Codec[Polygon]                       = geometryCodec[Polygon]
  val multiPoint: Codec[MultiPoint]                 = geometryCodec[MultiPoint]
  val multiLineString: Codec[MultiLineString]       = geometryCodec[MultiLineString]
  val multiPolygon: Codec[MultiPolygon]             = geometryCodec[MultiPolygon]
  val geometryCollection: Codec[GeometryCollection] = geometryCodec[GeometryCollection]

  private def geometryCodec[A >: Null <: Geometry](implicit A: ClassTag[A]): Codec[A] = {
    geometry.imap[A](geom => A.runtimeClass.cast(geom).asInstanceOf[A])(o =>
      o.asInstanceOf[Geometry]
    )
  }

}

object geometry extends PostGISGeometryCodecs

object all extends PostGISGeometryCodecs