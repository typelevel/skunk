// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import skunk.implicits._
import skunk.codec.all._
import skunk.exception.CopyNotSupportedException
import skunk.exception.NoDataException

class CopyOutTest extends SkunkTest {
  
  override def munitIgnore: Boolean = true  

  sessionTest("copy out (simple query)") { s =>
    s.execute(sql"COPY country TO STDOUT".query(int4))
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

  sessionTest("copy out (simple command)") { s =>
    s.execute(sql"COPY country TO STDOUT".command)
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

  sessionTest("copy out (extended, query)") { s =>
    s.prepare(sql"COPY country TO STDOUT".query(int4))
      .use { _ => IO.unit }
      .assertFailsWith[NoDataException] *> s.assertHealthy // can't distinguish this from other cases, probably will be an FAQ
  }

  sessionTest("copy out (extended command)") { s =>
    s.prepare(sql"COPY country TO STDOUT".command)
      .use { _.execute(skunk.Void) }
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

}