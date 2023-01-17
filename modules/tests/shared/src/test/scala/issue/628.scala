// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest

// https://github.com/tpolecat/skunk/issues/628
class Test628 extends SkunkTest {

  sessionTest("issue/628") { s =>
    s.prepare(sql"select name from country".query(varchar)).flatMap { ps =>
      ps.stream(Void, 10).compile.toList
    }
  }

}