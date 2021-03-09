// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import skunk.implicits._
import tests.SkunkTest
import skunk.data.Completion

// https://github.com/tpolecat/skunk/issues/406
class Test406 extends SkunkTest {

  sessionTest("issue/406") { s =>
    for {
      _ <- s.execute(sql"create temporary table foo (id int)".command)
      c <- s.execute(sql"truncate table foo".command)
      _ <- assertEqual("completion", c, Completion.Truncate)
    } yield "ok"
  }

}