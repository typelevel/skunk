// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package data

import skunk.implicits._
import skunk.Fragment
import skunk.Void

class StringContextOpsTest extends ffstest.FTest {

  test("allows Identifiers") {
    val t = ident"my_table"
    val f: Fragment[Void] = sql"SELECT * FROM $t"
    assertEqual("sql", f.sql, "SELECT * FROM my_table")
  }

  test("allows quoted Identifiers") {
    val t = ident"my_table; drop table my_table"
    val f: Fragment[Void] = sql"SELECT * FROM $t"
    assertEqual("sql", f.sql, "SELECT * FROM \"my_table; drop table my_table\"")
  }
}
