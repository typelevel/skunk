// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package postgis

import cats.Eq
import cats.data.NonEmptyList
import cats.syntax.all._

final case class SRID(value: Int)

sealed trait Dimension extends Product with Serializable

object Dimension {
  case object TwoD extends Dimension
  case object Z extends Dimension
  case object M extends Dimension
  case object ZM extends Dimension
}

sealed trait Geometry extends Product with Serializable {
  def srid: Option[SRID]
  def dimension: Dimension
}

object Geometry {
  implicit def geometryEq[A <: Geometry]: Eq[A] = new Eq[A] {
    override def eqv(x: A, y: A): Boolean = x.equals(y)
  }
}

final case class Coordinate(x: Double, y: Double, z: Option[Double], m: Option[Double]) {
  def hasZ: Boolean = z.isDefined
  def hasM: Boolean = m.isDefined

  def dimension: Dimension = (z, m) match {
    case (None, None)       => Dimension.TwoD
    case (Some(_), None)    => Dimension.Z
    case (None, Some(_))    => Dimension.M
    case (Some(_), Some(_)) => Dimension.ZM
  }

  def isEmpty: Boolean =
    x.isNaN() && y.isNaN() &&
      z.map(_.isNaN()).getOrElse(true) &&
      m.map(_.isNaN()).getOrElse(true)

  // Added to consider NaN equal in this case
  override def equals(other: Any): Boolean =
    other match {
      case other: Coordinate =>
        other.dimension == dimension &&
        other.isEmpty == isEmpty || (
          other.x == x &&
          other.y == y &&
          other.z == z &&
          other.m == m
        )
      case _ => super.equals(other)
    }

}

object Coordinate {
  def xy(x: Double, y: Double): Coordinate                         = Coordinate(x, y, None, None)
  def xyz(x: Double, y: Double, z: Double): Coordinate             = Coordinate(x, y, z.some, None)
  def xym(x: Double, y: Double, m: Double): Coordinate             = Coordinate(x, y, None, m.some)
  def xyzm(x: Double, y: Double, z: Double, m: Double): Coordinate = Coordinate(x, y, z.some, m.some)
}

final case class Point (srid: Option[SRID], coordinate: Coordinate) extends Geometry {
  override def dimension: Dimension = coordinate.dimension
}

object Point {
  def apply(coordinate: Coordinate): Point = Point(None, coordinate)
  def apply(srid: SRID, coordinate: Coordinate): Point = Point(srid.some, coordinate)
  
  def xy(x: Double, y: Double): Point                         = Point(None, Coordinate.xy(x, y))
  def xyz(x: Double, y: Double, z: Double): Point             = Point(None, Coordinate.xyz(x, y, z))
  def xym(x: Double, y: Double, m: Double): Point             = Point(None, Coordinate.xym(x, y, m))
  def xyzm(x: Double, y: Double, z: Double, m: Double): Point = Point(None, Coordinate.xyzm(x, y, z, m))

  def xy(srid: SRID, x: Double, y: Double): Point                         = Point(srid.some, Coordinate.xy(x, y))
  def xyz(srid: SRID, x: Double, y: Double, z: Double): Point             = Point(srid.some, Coordinate.xyz(x, y, z))
  def xym(srid: SRID, x: Double, y: Double, m: Double): Point             = Point(srid.some, Coordinate.xym(x, y, m))
  def xyzm(srid: SRID, x: Double, y: Double, z: Double, m: Double): Point = Point(srid.some, Coordinate.xyzm(x, y, z, m))
}

final case class LineString(srid: Option[SRID], dimensionHint: Dimension, coordinates: List[Coordinate]) extends Geometry {
  override def dimension: Dimension = coordinates.headOption.fold(dimensionHint)(_.dimension)
}

object LineString {
  def apply(coordinates: Coordinate*): LineString
    = apply(coordinates.toList)
  def apply(coordinates: List[Coordinate]): LineString =
    LineString(None, coordinates.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), coordinates)

  def apply(srid: SRID, coordinates: Coordinate*): LineString =
    apply(srid, coordinates.toList)
  def apply(srid: SRID, coordinates: List[Coordinate]): LineString =
    LineString(srid.some, coordinates.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), coordinates)
}

final case class LinearRing(coordinates: NonEmptyList[Coordinate])

object LinearRing {
  def apply(head: Coordinate, tail: Coordinate*): LinearRing =
    LinearRing(NonEmptyList(head, tail.toList))
}

final case class Polygon(srid: Option[SRID], dimensionHint: Dimension, shell: Option[LinearRing], holes: List[LinearRing]) extends Geometry {
  override def dimension: Dimension = shell.map(_.coordinates.head.dimension).getOrElse(dimensionHint)
}

object Polygon {
  def apply(shell: LinearRing): Polygon =
    Polygon(None, shell.coordinates.head.dimension, shell.some, Nil)
  def apply(shell: LinearRing, holes: LinearRing*): Polygon =
    Polygon(None, shell.coordinates.head.dimension, shell.some, holes.toList)

  def apply(srid: SRID, shell: LinearRing): Polygon =
    Polygon(srid.some, shell.coordinates.head.dimension, shell.some, Nil)
  def apply(srid: SRID, shell: LinearRing, holes: LinearRing*): Polygon =
    Polygon(srid.some, shell.coordinates.head.dimension, shell.some, holes.toList)
}

final case class MultiPoint(srid: Option[SRID], dimensionHint: Dimension, points: List[Point]) extends Geometry {
  override def dimension: Dimension = points.headOption.fold(dimensionHint)(_.dimension)
}

object MultiPoint {
  def apply(points: Point*): MultiPoint =
    MultiPoint(None, points.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), points.toList)

  def apply(srid: SRID, points: Point*): MultiPoint =
    MultiPoint(srid.some, points.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), points.toList)
}

final case class MultiLineString(srid: Option[SRID], dimensionHint: Dimension, lineStrings: List[LineString]) extends Geometry {
  override def dimension: Dimension = lineStrings.headOption.fold(dimensionHint)(_.dimension)
}

object MultiLineString {
  def apply(lineStrings: LineString*): MultiLineString =
    MultiLineString(None, lineStrings.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), lineStrings.toList)

  def apply(srid: SRID, lineStrings: LineString*): MultiLineString =
    MultiLineString(srid.some, lineStrings.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), lineStrings.toList)
}

final case class MultiPolygon(srid: Option[SRID], dimensionHint: Dimension, polygons: List[Polygon]) extends Geometry {
  override def dimension: Dimension = polygons.headOption.fold(dimensionHint)(_.dimension)
}

object MultiPolygon {
  def apply(polygons: Polygon*): MultiPolygon =
    MultiPolygon(None, polygons.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), polygons.toList)

  def apply(srid: SRID, polygons: Polygon*): MultiPolygon =
    MultiPolygon(srid.some, polygons.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), polygons.toList)
}

final case class GeometryCollection(srid: Option[SRID], dimensionHint: Dimension, geometries: List[Geometry]) extends Geometry {
  override def dimension: Dimension = geometries.headOption.fold(dimensionHint)(_.dimension)
}

object GeometryCollection {

  def apply(geometries: Geometry*): GeometryCollection =
    GeometryCollection(None, geometries.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), geometries.toList)

  def apply(srid: SRID, geometries: Geometry*): GeometryCollection =
    GeometryCollection(srid.some, geometries.headOption.fold[Dimension](Dimension.TwoD)(_.dimension), geometries.toList)
}
