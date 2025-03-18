// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import skunk.TypingStrategy
import skunk.postgis._
import skunk.postgis.codecs.all._

class PostGISCodecTest extends CodecTest(strategy = TypingStrategy.SearchPath) {

  roundtripTest(point)(Point.xy(1, 2))
  roundtripTest(point)(Point(SRID(4326), Coordinate.xy(1, 2)))
  roundtripTest(point)(Point.xyz(1, 2, 3))
  roundtripTest(point)(Point.xym(1, 2, 3))
  roundtripTest(point)(Point.xyzm(1, 2, 3, 4))

  roundtripTest(lineString)(
    LineString(
      Coordinate.xy(1, 2),
      Coordinate.xy(3, 4),
      Coordinate.xy(5, 6),
      Coordinate.xy(7, 8)
    )
  )
  roundtripTest(lineString)(
    LineString(
      Coordinate.xyz(1, 2, 1),
      Coordinate.xyz(3, 4, 1),
      Coordinate.xyz(5, 6, 1),
      Coordinate.xyz(7, 8, 1)
    )
  )

  roundtripTest(polygon)(
    Polygon(
      LinearRing(
        Coordinate.xy(0, 0),
        Coordinate.xy(1, 0),
        Coordinate.xy(1, 1),
        Coordinate.xy(0, 1),
        Coordinate.xy(0, 0)
      ),
    )
  )

  roundtripTest(multiPoint)(
    MultiPoint(
      Point.xy(1, 1),
      Point.xy(2, 2),
      Point.xy(3, 3)
    )
  )

  roundtripTest(multiLineString)(
    MultiLineString(
      SRID(4326),
      LineString(
        SRID(4326),
        Coordinate.xy(0, 0),
        Coordinate.xy(1, 1)
      ),
      LineString(
        SRID(4326),
        Coordinate.xy(2, 2),
        Coordinate.xy(3, 3)
      )
    )
  )

  roundtripTest(multiPolygon)(
    MultiPolygon(
      SRID(4326),
      Polygon(
        SRID(4326),
        LinearRing(
          Coordinate.xy(0, 0),
          Coordinate.xy(10, 10),
          Coordinate.xy(20, 20)
        ),
      )
    )
  )

  roundtripTest(geometryCollection)(
    GeometryCollection(
      SRID(4326),
      LineString(
        Coordinate.xy(1, 2),
        Coordinate.xy(3, 4),
        Coordinate.xy(5, 6),
        Coordinate.xy(7, 8)
      ),
      Point.xy(1, 2)
    )
  )

}
