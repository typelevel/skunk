// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.postgis
package ewkt

import cats.data.NonEmptyList
import cats.parse.Numbers
import cats.parse.{Parser => P}
import cats.parse.{Parser0 => P0}

object EWKT {

  def parse(str: String): Either[P.Error, Geometry] =
    geometry.parse(str).map(_._2)

  private[this] val whitespace: P[Unit] = P.charIn(" \t\r\n").void
  private[this] val whitespaces0: P[Unit] = whitespace.rep.void
  private[this] val comma: P[Unit] = P.char(',').surroundedBy(whitespaces0.?)

  private[this] implicit class BetweenOps[A](parser: P[A]) {
    implicit val betweenParens: P[A] =
      parser.between(P.char('('), P.char(')'))
    implicit val betweenParensOpt: P[A] =
      parser.between(P.char('(').?, P.char(')').?)
  }

  private[this] val srid: P[SRID] =
    Numbers.nonNegativeIntString.map(s => SRID(Integer.parseInt(s))).between(
      P.ignoreCase("SRID="),
      P.char(';')
    )

  private[this] def keyword(keyword: String): P[Dimension] =
    P.ignoreCase(keyword).surroundedBy(whitespaces0.?) *>
      dimension

  // This is only a hint, if you leave off ZM, but provide the 4 dimensions in the coordinate, it's still ZM
  // Both 'POINT( 1 2 3 4 )' and 'POINT ZM( 1 2 3 4 )' have the same dimension
  private[this] val dimension: P0[Dimension] = (
    P.ignoreCase("Z").surroundedBy(whitespaces0.?).? ~
    P.ignoreCase("M").surroundedBy(whitespaces0.?).?
  ).map { dimensionChars =>
    dimensionChars match {
      case (Some(_), None)    => Dimension.Z
      case (None, Some(_))    => Dimension.M
      case (Some(_), Some(_)) => Dimension.ZM
      case _                  => Dimension.TwoD
    }
  }

  private[this] def empty[A](value: A): P[A] =
    P.ignoreCase("EMPTY").surroundedBy(whitespaces0.?).as(value)

  private[this] val double: P[Double] =
    Numbers.jsonNumber.map(s => BigDecimal(s).toDouble)
    
  private[this] def coordinate(implicit dimensionHint: Dimension): P[Coordinate] =
    double.surroundedBy(whitespaces0.?).rep(2, 4).flatMap { elements =>
      (elements, dimensionHint) match {
        case (NonEmptyList(x, y :: Nil), Dimension.TwoD)           => P.pure(Coordinate.xy(x, y))
        case (NonEmptyList(x, y :: z :: Nil), Dimension.TwoD)      => P.pure(Coordinate.xyz(x, y, z))
        case (NonEmptyList(x, y :: z :: Nil), Dimension.Z)         => P.pure(Coordinate.xyz(x, y, z))
        case (NonEmptyList(x, y :: m :: Nil), Dimension.M)         => P.pure(Coordinate.xym(x, y, m))
        case (NonEmptyList(x, y :: z :: m :: Nil), Dimension.ZM)   => P.pure(Coordinate.xyzm(x, y, z, m))
        case (NonEmptyList(x, y :: z :: m :: Nil), Dimension.TwoD) => P.pure(Coordinate.xyzm(x, y, z, m))
        case _                                                     => P.failWith(s"""Invalid Geometry Dimensionality [$dimensionHint]: ${elements.toList.mkString(",")}""")
      }
    }

  private[this] def coordinateEmpty(implicit dimensionHint: Dimension): P[Coordinate] =
    P.ignoreCase("EMPTY").surroundedBy(whitespaces0.?).as(
      dimensionHint match {
        case Dimension.TwoD => Coordinate.xy(Double.NaN, Double.NaN)
        case Dimension.Z    => Coordinate.xyz(Double.NaN, Double.NaN, Double.NaN)
        case Dimension.M    => Coordinate.xym(Double.NaN, Double.NaN, Double.NaN)
        case Dimension.ZM   => Coordinate.xyzm(Double.NaN, Double.NaN, Double.NaN, Double.NaN)
      }
    )

  private[this] def coordinates(implicit dimensionHint: Dimension): P[NonEmptyList[Coordinate]] =
    coordinate.repSep(comma).betweenParens

  /////////////////////////////////////////////////////////////////////////////

  private[this] def nonEmptyPoint(implicit srid: Option[SRID], dimensionHint: Dimension): P[Point] =
    coordinate.betweenParens.map(c => Point(srid, c))

  private[this] def nonEmptyLineString(implicit srid: Option[SRID], dimensionHint: Dimension): P[LineString] =
    coordinate.repSep(comma).betweenParens.map(points =>
      LineString(srid, dimensionHint, points.toList))

  private[this] def nonEmptyPolygon(implicit srid: Option[SRID], dimensionHint: Dimension): P[Polygon] =
    coordinates.map(LinearRing.apply).repSep(comma).betweenParens.map(rings =>
      Polygon(srid, dimensionHint, Some(rings.head), rings.tail))

  private[this] def nonEmptyMultiPoint(implicit srid: Option[SRID], dimensionHint: Dimension): P[MultiPoint] =
    coordinate.betweenParensOpt.repSep(comma)
      .map(nel => nel.map(c => Point(c))).betweenParensOpt
      .map(points => MultiPoint(srid, dimensionHint, points.toList))

  private[this] def nonEmptyMultiLineString(implicit srid: Option[SRID], dimensionHint: Dimension): P[MultiLineString] =
    nonEmptyLineString.repSep(comma).betweenParens.map(lineStrings =>
      MultiLineString(srid, dimensionHint, lineStrings.toList))

  private[this] def nonEmptyMultiPolygon(implicit srid: Option[SRID], dimensionHint: Dimension): P[MultiPolygon] =
    nonEmptyPolygon.repSep(comma).betweenParens.map(polygons =>
      MultiPolygon(srid, dimensionHint, polygons.toList))

  private[this] def nonEmptyGeometryCollection(implicit srid: Option[SRID], dimensionHint: Dimension): P[GeometryCollection] =
    P.oneOf(
      point ::
      lineString ::
      polygon ::
      multiPoint ::
      multiLineString ::
      multiPolygon :: 
      geometryCollection ::
      Nil
    ).repSep(comma).betweenParens.map(geometries =>
      GeometryCollection(srid, dimensionHint, geometries.toList)
    )

  /////////////////////////////////////////////////////////////////////////////

  def point: P[Point] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("POINT").flatMap { implicit dimensionHint =>
        P.oneOf(
          coordinateEmpty.map(c => Point(srid, c)) ::
          nonEmptyPoint ::
          Nil
        )
      }
    }

  val lineString: P[LineString] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("LINESTRING").flatMap { implicit dimensionHint =>
        P.oneOf(
          empty(LineString(srid, dimensionHint, Nil)) ::
          nonEmptyLineString ::
          Nil
        )
      }
    }

  val polygon: P[Polygon] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("POLYGON").flatMap { implicit dimensionHint =>
        P.oneOf(
          empty(Polygon(srid, dimensionHint, None, Nil)) ::
          nonEmptyPolygon ::
          Nil
        )
      }
    }

  val multiPoint: P[MultiPoint] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("MULTIPOINT").flatMap { implicit dimensionHint =>
        P.oneOf(
          empty(MultiPoint(srid, dimensionHint, Nil)) ::
          nonEmptyMultiPoint ::
          Nil
        )
      }
    }

  val multiLineString: P[MultiLineString] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("MULTILINESTRING").flatMap { implicit dimensionHint =>
        P.oneOf(
          empty(MultiLineString(srid, dimensionHint, Nil)) ::
          nonEmptyMultiLineString ::
          Nil
        )
      }
    }

  val multiPolygon: P[MultiPolygon] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("MULTIPOLYGON").flatMap { implicit dimensionHint =>
        P.oneOf(
          empty(MultiPolygon(srid, dimensionHint, Nil)) ::
          nonEmptyMultiPolygon ::
          Nil
        )
      }
    }

  val geometryCollection: P[GeometryCollection] =
    P.flatMap01(srid.?) { implicit srid =>
      keyword("GEOMETRYCOLLECTION").flatMap { implicit dimensionHint =>
        P.oneOf(
          empty(GeometryCollection(srid, dimensionHint, Nil)) ::
          nonEmptyGeometryCollection ::
          Nil
        )
      }
    }

  val geometry: P[Geometry] =
    P.oneOf(
      point.backtrack ::
      lineString.backtrack ::
      polygon.backtrack ::
      multiPoint.backtrack ::
      multiLineString.backtrack ::
      multiPolygon.backtrack ::
      geometryCollection.backtrack ::
      Nil
    )
}