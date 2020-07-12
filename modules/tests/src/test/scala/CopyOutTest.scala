// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.exception.CopyNotSupportedException

object CopyOutTest extends SkunkTest {

  sessionTest("copy out") { s =>
    s.execute(sql"COPY country TO STDOUT".query(int4))
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

  sessionTest("copy out (as command)") { s =>
    s.execute(sql"COPY country TO STDOUT".command)
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

}