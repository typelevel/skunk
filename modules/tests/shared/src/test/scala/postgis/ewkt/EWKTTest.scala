// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.postgis.ewkt

import skunk.postgis.ewkt.EWKT

class EWKTTest extends munit.FunSuite {

  ewktTest("POINT EMPTY")
  ewktTest("POINT Z EMPTY")
  ewktTest("POINT M EMPTY")
  ewktTest("POINTZM EMPTY")
  ewktTest("POINT(0 0)")
  ewktTest("POINT Z (1 2 3)")
  ewktTest("POINT M (1 2 3)")
  ewktTest("POINT ZM (1 2 3 4)")
  ewktTest("POINT(1 2 3 4)")

  ewktTest("LINESTRING EMPTY")
  ewktTest("LINESTRING Z EMPTY")
  ewktTest("LINESTRING M EMPTY")
  ewktTest("LINESTRING ZM EMPTY")
  ewktTest("LINESTRING(0 0, 1 1)")
  ewktTest("LINESTRING Z (0 0 2, 1 1 3)")
  ewktTest("LINESTRING M (0 0 2, 1 1 3)")
  ewktTest("LINESTRING ZM (0 0 2 3, 1 1 4 5)")

  ewktTest("POLYGON EMPTY")
  ewktTest("POLYGON Z EMPTY")
  ewktTest("POLYGON M EMPTY")
  ewktTest("POLYGON ZM EMPTY")
  ewktTest("POLYGON((0 0,1 0,1 1,0 1,0 0))")
  ewktTest("POLYGON((0 0,10 0,10 10,0 10,0 0),(2 2,2 5,5 5,5 2,2 2))")
  ewktTest("POLYGON Z ((0 0 1,10 0 2 ,10 10 2,0 10 2,0 0 1),(2 2 5 ,2 5 4,5 5 3,5 2 3,2 2 5))")
  ewktTest("POLYGON M ((0 0 1,10 0 2 ,10 10 2,0 10 2,0 0 1),(2 2 5 ,2 5 4,5 5 3,5 2 3,2 2 5))")
  ewktTest("POLYGON ZM ((0 0 1 -1,10 0 2 -2,10 10 2 -2,0 10 2 -4,0 0 1 -1),(2 2 5 0,2 5 4 1,5 5 3 2,5 2 3 1,2 2 5 0))")

  ewktTest("MULTIPOINT EMPTY")
  ewktTest("MULTIPOINT Z EMPTY")
  ewktTest("MULTIPOINT M EMPTY")
  ewktTest("MULTIPOINT ZM EMPTY")
  ewktTest("MULTIPOINT((0 0), 2 0)")
  ewktTest("MULTIPOINT Z ((0 0 0), (2 0 1))")
  ewktTest("MULTIPOINT M ((0 0 2), (2 0 1))")
  ewktTest("MULTIPOINT ZM ((0 1 2 3), (3 2 1 0))")

  ewktTest("MULTILINESTRING EMPTY")
  ewktTest("MULTILINESTRING Z EMPTY")
  ewktTest("MULTILINESTRING M EMPTY")
  ewktTest("MULTILINESTRING ZM EMPTY")
  ewktTest("MULTILINESTRING((0 0, 2 0))")
  ewktTest("MULTILINESTRING((0 0, 2 0), (1 1, 2 2))")
  ewktTest("MULTILINESTRING Z ((0 0 1, 2 0 2), (1 1 3, 2 2 4))")
  ewktTest("MULTILINESTRING M ((0 0 1, 2 0 2), (1 1 3, 2 2 4))")
  ewktTest("MULTILINESTRING ZM ((0 0 1 5, 2 0 2 4), (1 1 3 3, 2 2 4 2))")

  ewktTest("MULTIPOLYGON EMPTY")
  ewktTest("MULTIPOLYGON Z EMPTY")
  ewktTest("MULTIPOLYGON M EMPTY")
  ewktTest("MULTIPOLYGON ZM EMPTY")
  ewktTest("MULTIPOLYGON(((0 0,10 0,10 10,0 10,0 0),(2 2,2 5,5 5,5 2,2 2)))")
  ewktTest("MULTIPOLYGON Z (((0 0 3,10 0 3,10 10 3,0 10 3,0 0 3),(2 2 3,2 5 3,5 5 3,5 2 3,2 2 3)))")
  ewktTest("MULTIPOLYGON M (((0 0 3,10 0 3,10 10 3,0 10 3,0 0 3),(2 2 3,2 5 3,5 5 3,5 2 3,2 2 3)))")
  ewktTest("MULTIPOLYGON ZM (((0 0 3 2,10 0 3 2,10 10 3 2,0 10 3 2,0 0 3 2),(2 2 3 2,2 5 3 2,5 5 3 2,5 2 3 2,2 2 3 2)))")
  ewktTest("MULTIPOLYGON(((0 0,4 0,4 4,0 4,0 0),(1 1,2 1,2 2,1 2,1 1)), ((-1 -1,-1 -2,-2 -2,-2 -1,-1 -1)))")

  ewktTest("GEOMETRYCOLLECTION EMPTY")
  ewktTest("GEOMETRYCOLLECTION Z EMPTY")
  ewktTest("GEOMETRYCOLLECTION M EMPTY")
  ewktTest("GEOMETRYCOLLECTION ZM EMPTY")
  ewktTest("GEOMETRYCOLLECTION ZM (POINT ZM (0 0 0 0),LINESTRING ZM (0 0 0 0,1 1 1 1))")
  ewktTest("GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1))")
  ewktTest("GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1),GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1)))")
  ewktTest("GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1),POINT M EMPTY,GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1)))")
  ewktTest("GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1),GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1),POINT M EMPTY,GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1))),POINT M EMPTY,GEOMETRYCOLLECTION M (POINT M (0 0 0),LINESTRING M (0 0 0,1 1 1)))")

  def ewktTest(ewkt: String) =
    test(ewkt) {
      EWKT.geometry.parse(ewkt).fold(
        error  => fail(s"[EWKT] Failed to parse [$ewkt]: ${error}"),
        result => assert(result._1.isEmpty())
      )
    }

}