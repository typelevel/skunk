// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import skunk.exception.EofException

class DisconnectTest extends SkunkTest {

  pooledTest("disconnect/reconnect", max = 1) { p =>
    p.use { s => // this session will be invalidated
      s.execute(sql"select pg_terminate_backend(pg_backend_pid())".query(bool))
    }.assertFailsWith[EofException] *>
    p.use { s => // this should be a *new* session, since the old one was busted
      s.execute(sql"select 1".query(int4))
    }
  }

}
