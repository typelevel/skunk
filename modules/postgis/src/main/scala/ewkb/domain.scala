// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.postgis
package ewkb

case class EWKBType(
  geometry: EWKBGeometry,
  coordinate: Dimension,
  srid: EWKBSRID
)

object EWKBType {

  def fromRaw(raw: Long): EWKBType = {
    EWKBType(
      EWKBGeometry.fromRaw(raw),
      dimensionFromRaw(raw),
      EWKBSRID.fromRaw(raw)
    )
  }

  def toRaw(ewkb: EWKBType): Long =
    EWKBGeometry.toRaw(ewkb.geometry) | dimensionToRaw(ewkb.coordinate) | EWKBSRID.toRaw(ewkb.srid)

  def fromGeometry(geometry: skunk.postgis.Geometry): EWKBType =
    EWKBType(
      EWKBGeometry.fromGeometry(geometry),
      geometry.dimension,
      EWKBSRID.fromGeometry(geometry)
    )

  private val ZMask = 0x80000000L
  private val MMask = 0x40000000L

  private def dimensionFromRaw(raw: Long): Dimension = {

    val hasZ = (raw & ZMask) == ZMask || (raw & 0xffff) / 1000 == 1 || (raw & 0xffff) / 1000 == 3
    val hasM = (raw & MMask) == MMask || (raw & 0xffff) / 1000 == 2 || (raw & 0xffff) / 1000 == 3

    (hasZ, hasM) match {
      case (false, false) => Dimension.TwoD
      case (true, false)  => Dimension.Z
      case (false, true)  => Dimension.M
      case (true, true)   => Dimension.ZM
    }
  }

  private def dimensionToRaw(coordinateType: Dimension): Long = coordinateType match {
    case Dimension.TwoD => 0L
    case Dimension.Z    => ZMask
    case Dimension.M    => MMask
    case Dimension.ZM   => ZMask | MMask
  }
}

sealed trait EWKBGeometry extends Product with Serializable

object EWKBGeometry {

  case object Point              extends EWKBGeometry
  case object LineString         extends EWKBGeometry
  case object Polygon            extends EWKBGeometry
  case object MultiPoint         extends EWKBGeometry
  case object MultiLineString    extends EWKBGeometry
  case object MultiPolygon       extends EWKBGeometry
  case object GeometryCollection extends EWKBGeometry

  def fromRaw(id: Long): EWKBGeometry = (id & 0x000000ff) match {
    case 1 => EWKBGeometry.Point
    case 2 => EWKBGeometry.LineString
    case 3 => EWKBGeometry.Polygon
    case 4 => EWKBGeometry.MultiPoint
    case 5 => EWKBGeometry.MultiLineString
    case 6 => EWKBGeometry.MultiPolygon
    case 7 => EWKBGeometry.GeometryCollection
    case _ => throw new IllegalArgumentException(s"Invalid (or unsupported) EWKB geometry type($id), expected [1-7]")
  }

  def toRaw(geometryType: EWKBGeometry): Long = geometryType match {
    case Point              => 1
    case LineString         => 2
    case Polygon            => 3
    case MultiPoint         => 4
    case MultiLineString    => 5
    case MultiPolygon       => 6
    case GeometryCollection => 7
  }

  def fromGeometry(geometry: Geometry): EWKBGeometry = geometry match {
    case _: Point              => Point
    case _: LineString         => LineString
    case _: Polygon            => Polygon
    case _: MultiPoint         => MultiPoint
    case _: MultiLineString    => MultiLineString
    case _: MultiPolygon       => MultiPolygon
    case _: GeometryCollection => GeometryCollection
  }
}

sealed trait EWKBSRID extends Product with Serializable

object EWKBSRID {
  case object Present extends EWKBSRID
  case object Absent extends EWKBSRID

  val Mask = 0x20000000L

  def fromRaw(raw: Long): EWKBSRID =
    if((raw & Mask) == Mask)
      EWKBSRID.Present
    else
      EWKBSRID.Absent

  def toRaw(sridEmbedded: EWKBSRID): Long = sridEmbedded match {
    case Absent  => 0L
    case Present => Mask
  }

  def fromGeometry(geometry: skunk.postgis.Geometry): EWKBSRID =
    geometry.srid.fold[EWKBSRID](EWKBSRID.Absent)(_ => EWKBSRID.Present)
}