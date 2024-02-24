// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.postgis
package ewkb

import cats.data.NonEmptyList
import cats.syntax.all._
import scodec.Attempt
import scodec.SizeBound
import scodec.bits.BitVector
import scodec.bits.ByteOrdering
import scodec.codecs._
import scodec.{Codec => Scodec}

trait EWKBCodecs extends EWKBCodecPlatform {

  lazy val geometry: Scodec[Geometry] = Scodec[Geometry](encoder, decoder)

  private def decoder: scodec.Decoder[Geometry] = {
    byteOrdering.flatMap { implicit byteOrdering =>
      ewkbType.flatMap { implicit ewkb =>
        ewkb.geometry match {
          case EWKBGeometry.Point              => point
          case EWKBGeometry.LineString         => lineString
          case EWKBGeometry.Polygon            => polygon
          case EWKBGeometry.MultiPoint         => multiPoint
          case EWKBGeometry.MultiLineString    => multiLineString
          case EWKBGeometry.MultiPolygon       => multiPolygon
          case EWKBGeometry.GeometryCollection => geometryCollection
        }
      }
    }
  }

  def encoder: scodec.Encoder[Geometry] = new scodec.Encoder[Geometry] {
    override def encode(value: Geometry): Attempt[BitVector] = {
      implicit val byteOrder: ByteOrdering = ByteOrdering.LittleEndian

      implicit val ewkb = EWKBType.fromGeometry(value)

      val geoencoder = value match {
        case _: Point              => point.upcast[Geometry]
        case _: LineString         => lineString.upcast[Geometry]
        case _: Polygon            => polygon.upcast[Geometry]
        case _: MultiPoint         => multiPoint.upcast[Geometry]
        case _: MultiLineString    => multiLineString.upcast[Geometry]
        case _: MultiPolygon       => multiPolygon.upcast[Geometry]
        case _: GeometryCollection => geometryCollection.upcast[Geometry]
      }

      // scala 2/3 platform specific call - scodec1 encodes HLists, scodec2 encodes tuples
      ewkbEncode(ewkb, value, geoencoder)
    }

    override def sizeBound: SizeBound = SizeBound.unknown
  }
}

object codecs extends EWKBCodecs

trait EWKBPrimitives {

  def geometry: Scodec[Geometry]
  
  def byteOrdering: Scodec[ByteOrdering] = byte.xmap(
    b => b match {
      case 0 => ByteOrdering.BigEndian
      case 1 => ByteOrdering.LittleEndian
    },
    o => o match {
      case ByteOrdering.BigEndian    => 0
      case ByteOrdering.LittleEndian => 1
    }
  )

  def ewkbType(implicit byteOrdering: ByteOrdering): Scodec[EWKBType] =
    guint32.xmap(EWKBType.fromRaw, EWKBType.toRaw)

  def srid(implicit byteOrderering: ByteOrdering, ewkb: EWKBType): Scodec[Option[SRID]] = {
    optional(provide(ewkb.srid == EWKBSRID.Present), gint32.xmap(SRID.apply, _.value))
  }

  def coordinate(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[Coordinate] = {
    ewkb.coordinate match {
      case Dimension.TwoD => gdouble :: gdouble :: provide(none[Double]) :: provide(none[Double])
      case Dimension.Z    => gdouble :: gdouble :: gdoubleOpt            :: provide(none[Double])
      case Dimension.M    => gdouble :: gdouble :: provide(none[Double]) :: gdoubleOpt
      case Dimension.ZM   => gdouble :: gdouble :: gdoubleOpt            :: gdoubleOpt
    }
  }.as[Coordinate]

  def point(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[Point] = {
    ewkb.coordinate match {
      case Dimension.TwoD => srid :: coordinate
      case Dimension.Z    => srid :: coordinate
      case Dimension.M    => srid :: coordinate
      case Dimension.ZM   => srid :: coordinate
    }
  }.as[Point]

  def lineString(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[LineString] = {
    srid :: provide(ewkb.coordinate) :: listOfN(gint32, coordinate)
  }.as[LineString]

  private case class PolygonRepr(srid: Option[SRID], dim: Dimension, rings: List[LinearRing])
  def polygon(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[Polygon] = {
    (srid :: provide(ewkb.coordinate) :: listOfN(gint32, linearRing)).as[PolygonRepr].xmap[Polygon](
      repr => Polygon(repr.srid, repr.dim, repr.rings.headOption, repr.rings.drop(1)),
      p => PolygonRepr(p.srid, p.dimension, p.shell.toList ::: p.holes)
    )
  }

  def linearRing(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[LinearRing] = {
    listOfN(gint32, coordinate).xmap[NonEmptyList[Coordinate]](NonEmptyList.fromListUnsafe, _.toList)
  }.as[LinearRing]

  def multiPoint(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[MultiPoint] = {
    srid :: provide(ewkb.coordinate) :: listOfN(gint32, geometry.downcast[Point]).xmap[List[Point]](
        pts => pts.map(_.copy(srid = None)),
        identity
      )
  }.as[MultiPoint]

  def multiLineString(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[MultiLineString] = {
    srid.flatPrepend { srid =>  
      provide(ewkb.coordinate) :: listOfN(gint32, geometry.downcast[LineString]).xmap[List[LineString]](
        lss => lss.map(_.copy(srid = srid)),
        identity
      )
    }
  }.as[MultiLineString]

  def multiPolygon(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[MultiPolygon] = {
    srid.flatPrepend { srid =>  
      provide(ewkb.coordinate) :: listOfN(gint32, geometry.downcast[Polygon]).xmap[List[Polygon]](
        ps => ps.map(_.copy(srid = srid)),
        identity
      )
    }
  }.as[MultiPolygon]

  def geometryCollection(implicit byteOrdering: ByteOrdering, ewkb: EWKBType): Scodec[GeometryCollection] = {
    srid :: provide(ewkb.coordinate) :: listOfN(gint32, geometry)
  }.as[GeometryCollection]

  def gint32(implicit byteOrdering: ByteOrdering) =
    gcodec(int32, int32L)
  def guint32(implicit byteOrdering: ByteOrdering) =
    gcodec(uint32, uint32L)
  def gdouble(implicit byteOrdering: ByteOrdering) =
    gcodec(double, doubleL)
  def gdoubleOpt(implicit byteOrdering: ByteOrdering): Scodec[Option[Double]] =
    gdouble.widenOpt(x => Some(x), x => x)

  def gcodec[A](big: Scodec[A], little: Scodec[A])(implicit byteOrdering: ByteOrdering) =
    byteOrdering match {
      case ByteOrdering.BigEndian    => big
      case ByteOrdering.LittleEndian => little
    }
    
}
