// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.exception.EmptyStatementException

object EmptyStatementTest extends SkunkTest {

  sessionTest("empty query") { s =>
    s.execute(sql"".query(int4))
      .assertFailsWith[EmptyStatementException] *> s.assertHealthy
  }

  sessionTest("comment-only query") { s =>
    s.execute(sql"-- only a comment!".query(int4))
      .assertFailsWith[EmptyStatementException] *> s.assertHealthy
  }

}