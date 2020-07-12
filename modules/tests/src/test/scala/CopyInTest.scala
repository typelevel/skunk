// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.exception.CopyNotSupportedException

object CopyInTest extends SkunkTest {

  sessionTest("copy in") { s =>
    s.execute(sql"COPY country FROM STDIN".query(int4))
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

}