// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import scala.concurrent.duration._
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

  pooledTest("listen fails when the connection is lost", max = 1) { p =>
    p.use { s => // this session will be invalidated, so its release fails too
      for {
        fib <- s.channel(id"disconnect_test").listen(42).compile.drain.start
        _   <- IO.sleep(1.second) // give the fiber time to issue LISTEN (see the race note in ChannelTest)
        _   <- s.execute(sql"select pg_terminate_backend(pg_backend_pid())".query(bool)).assertFailsWith[EofException]
        oc  <- fib.join.timeout(10.seconds) // hangs forever if the failure is not propagated to the stream
        _   <- oc match {
                 case Outcome.Errored(_) => IO.unit
                 case o                  => fail[Unit](s"expected listen stream to fail, got $o")
               }
      } yield ()
    }.assertFailsWith[EofException] *> IO.pure("ok")
  }

}
