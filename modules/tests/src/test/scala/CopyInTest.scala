// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.exception.CopyNotSupportedException
import cats.effect.IO
import skunk.exception.NoDataException

class CopyInTest extends SkunkTest {

  sessionTest("copy in") { s =>
    s.execute(sql"COPY country FROM STDIN".query(int4))
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

  sessionTest("copy in (as command)") { s =>
    s.execute(sql"COPY country FROM STDIN".command)
      .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  }

  // sessionTest("copy in (extended, query)") { s =>
  //   s.prepare(sql"COPY country FROM STDIN".query(int4))
  //     .use { _ => IO.unit }
  //     .assertFailsWith[NoDataException] *> s.assertHealthy // can't distinguish this from other cases, probably will be an FAQ
  // }

  // sessionTest("copy in (extended command)") { s =>
  //   s.prepare(sql"COPY country FROM STDIN".command)
  //     .use { _.execute(skunk.Void) }
  //     .assertFailsWith[CopyNotSupportedException] *> s.assertHealthy
  // }

}