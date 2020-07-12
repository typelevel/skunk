// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import skunk.implicits._
import skunk.codec.all._
import skunk.exception.EmptyStatementException
import skunk.exception.NoDataException

object EmptyStatementTest extends SkunkTest {

  sessionTest("comment-only query") { s =>
    s.execute(sql"-- only a comment!".query(int4))
      .assertFailsWith[EmptyStatementException] *> s.assertHealthy
  }

  sessionTest("empty query (simple query)") { s =>
    s.execute(sql"".query(int4))
      .assertFailsWith[EmptyStatementException] *> s.assertHealthy
  }

  sessionTest("empty query (simple command)") { s =>
    s.execute(sql"".command)
      .assertFailsWith[EmptyStatementException] *> s.assertHealthy
  }

  sessionTest("empty query (extended query)") { s =>
    s.prepare(sql"".query(int4)).use(_ => IO.unit)
      .assertFailsWith[NoDataException] *> s.assertHealthy
  }

  sessionTest("empty query (extended command)") { s =>
    s.prepare(sql"".command).use(_.execute(skunk.Void))
      .assertFailsWith[EmptyStatementException] *> s.assertHealthy
  }

}