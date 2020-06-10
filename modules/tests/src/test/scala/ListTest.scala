// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.codec.all._
import skunk.implicits._

case object ListTest extends SkunkTest {

 // Regression test for https://github.com/tpolecat/skunk/issues/148
  sessionTest("Do not throw StackOverflowError on big list encoders") { s =>
    val aLotOfStrings = List.fill(Short.MaxValue)("foo")
    val aLotOfStringsCodec = text.values.list(aLotOfStrings.length)
    val bigValuesCommand: Query[List[String], String] =
      sql"SELECT * FROM (VALUES $aLotOfStringsCodec) AS tmp LIMIT 1".query(text)

    for {
      res <- s.prepare(bigValuesCommand).use(q => q.unique(aLotOfStrings))
      _ <- assert("read", res == "foo")
      _ <- s.assertHealthy
    } yield "ok"
  }

}

