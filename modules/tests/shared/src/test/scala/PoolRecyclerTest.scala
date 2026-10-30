// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.{ IO, Resource }
import cats.syntax.all._
import munit.Location
import skunk.Session
import skunk.codec.all._
import skunk.data.TransactionStatus
import skunk.exception.PostgresErrorException
import skunk.implicits._
import skunk.util.Recycler

class PoolRecyclerTest extends SkunkTest {

  val backendPid = sql"select pg_backend_pid()".query(int4)
  val appName    = sql"show application_name".query(text)
  val setAppName = sql"set application_name = 'recycler-test'".command

  def pooledWith(recycler: Recycler[IO, Session[IO]])(name: String)(fa: Resource[IO, Session[IO]] => IO[Unit])(implicit loc: Location): Unit =
    tracedTest(name) { implicit tracer =>
      Session.Builder[IO]
        .withUserAndPassword("jimmy", "banana")
        .withDatabase("world")
        .withRecycler(recycler)
        .pooled(1)
        .use(fa)
    }

  pooledWith(Session.Recyclers.full[IO])("full recycler (default) resets session state") { pool =>
    for {
      pid1 <- pool.use(s => s.execute(setAppName) *> s.unique(backendPid))
      res  <- pool.use(s => (s.unique(backendPid), s.unique(appName)).tupled)
      _    <- assertEqual("same session", res._1, pid1)
      _    <- assertEqual("application_name reset", res._2 == "recycler-test", false)
    } yield ()
  }

  pooledWith(Session.Recyclers.idle[IO])("idle recycler reuses session without resetting state") { pool =>
    for {
      pid1 <- pool.use(s => s.execute(setAppName) *> s.unique(backendPid))
      res  <- pool.use(s => (s.unique(backendPid), s.unique(appName)).tupled)
      _    <- assertEqual("same session", res._1, pid1)
      _    <- assertEqual("application_name retained, so no RESET ALL", res._2, "recycler-test")
    } yield ()
  }

  pooledWith(Session.Recyclers.idle[IO])("idle recycler discards session left in a transaction") { pool =>
    for {
      pid1 <- pool.use { s =>
                s.execute(sql"begin".command) *>
                s.assertTransactionStatus("in transaction", TransactionStatus.Active) *>
                s.unique(backendPid)
              }
      pid2 <- pool.use(s => s.assertTransactionStatus("new session", TransactionStatus.Idle) *> s.unique(backendPid))
      _    <- assertEqual("different session", pid2 == pid1, false)
    } yield ()
  }

  pooledWith(Session.Recyclers.idle[IO])("idle recycler discards session left in a failed transaction") { pool =>
    for {
      pid1 <- pool.use { s =>
                for {
                  pid <- s.unique(backendPid)
                  _   <- s.execute(sql"begin".command)
                  _   <- s.execute(sql"select 1/0".query(int4)).assertFailsWith[PostgresErrorException]
                  _   <- s.assertTransactionStatus("failed transaction", TransactionStatus.Failed)
                } yield pid
              }
      pid2 <- pool.use(s => s.assertTransactionStatus("new session", TransactionStatus.Idle) *> s.unique(backendPid))
      _    <- assertEqual("different session", pid2 == pid1, false)
    } yield ()
  }

  pooledWith(Recycler.success[IO, Session[IO]])("custom recycler is used") { pool =>
    for {
      pid1 <- pool.use(s => s.execute(sql"begin".command) *> s.unique(backendPid))
      res  <- pool.use(s => (s.unique(backendPid), s.transactionStatus.get).tupled)
      _    <- assertEqual("same session", res._1, pid1)
      _    <- assertEqual("still in transaction", res._2, TransactionStatus.Active)
      _    <- pool.use(_.execute(sql"rollback".command))
    } yield ()
  }

}
