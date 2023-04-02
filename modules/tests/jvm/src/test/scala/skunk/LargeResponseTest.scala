// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package test

import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest

class LargeResponseTest extends SkunkTest {
    // Even when streaming the results and not accumulating them, we see same time ~12s 
    sessionTest("large row stream benchmark") { s => 
      val query = sql"""select generate_series(1,500000)""".query(int4)
      for {
        res <- s.stream(query, Void, 64000).compile.drain.timed
        // res <- s.execute(query).timed
        (duration, r) = res
        _ = println(s"Took ${duration.toMillis} to stream 500K rows to /dev/null")
      } yield "ok"
    }
}
