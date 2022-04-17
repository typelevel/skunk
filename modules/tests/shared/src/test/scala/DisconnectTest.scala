// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.implicits._
import skunk.codec.all._
import cats.effect._
import skunk.Session
import natchez.Trace.Implicits.noop
import skunk.exception.EofException
import ffstest.FTest

class DisconnectTest extends FTest {

  val pool: Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled[IO](
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      max      = 1, // ensure that the session is reused if possible
      // debug = true,
    ).map(_.apply(natchez.Trace[IO]))

  test("disconnect/reconnect") {
    pool.use { p =>
      p.use { s => // this session will be invalidated
        s.execute(sql"select pg_terminate_backend(pg_backend_pid())".query(bool))
      }.assertFailsWith[EofException] *>
      p.use { s => // this should be a *new* session, since the old one was busted
        s.execute(sql"select 1".query(int4))
      }
    }
  }

}