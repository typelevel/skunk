// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import cats.syntax.all._
import skunk.implicits._
import skunk.codec.all._
import skunk.exception.EofException
import scala.concurrent.duration._

class DisconnectTest extends SkunkTest {

  pooledTest("disconnect/reconnect", max = 1) { p =>
    p.use { s => // this session will be invalidated
      s.execute(sql"select pg_terminate_backend(pg_backend_pid())".query(bool))
    }.assertFailsWith[EofException] *>
    p.use { s => // this should be a *new* session, since the old one was busted
      s.execute(sql"select 1".query(int4))
    }
  }

  sessionTest("isHealthy becomes false once the connection is terminated") { s =>
    for {
      _ <- s.isHealthy.flatMap(assert("a fresh session is healthy", _))
      _ <- s.execute(sql"select pg_terminate_backend(pg_backend_pid())".query(bool))
             .assertFailsWith[EofException]
      _ <- s.isHealthy.flatMap(h => assert("a terminated session is not healthy", !h))
    } yield ()
  }

  tracedTest("disconnect while idle in pool") { implicit tracer =>
    pooled(max = 1).use { p =>
      for {
        sp        <- p.use(s => s.unique(sql"select pg_backend_pid()".query(int4)).tupleLeft(s))
        (s, pid)   = sp
        _         <- session.use(_.unique(sql"select pg_terminate_backend($int4)".query(bool))(pid))
        _         <- (IO.sleep(10.millis) *> s.isHealthy).iterateWhile(identity).timeout(10.seconds)
        pid2      <- p.use(_.unique(sql"select pg_backend_pid()".query(int4)))
        _         <- assert(s"expected a new backend, got $pid twice", pid =!= pid2)
      } yield ()
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
    }
  }
}
