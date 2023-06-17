// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all._
import org.typelevel.otel4s.trace.Tracer
import skunk.codec.numeric.int4
import skunk.codec.text
import skunk._
import skunk.exception.PostgresErrorException
import skunk.implicits._

class DescribeCacheTest extends SkunkTest(true) {

  implicit val tracer: Tracer[IO] = Tracer.noop

  // N.B. this checks that statements are cached, but it _doesn't_ check that the cache is observed
  // by the `Describe` protocol implementation. There's not an easy way to do this without exposing
  // a bunch of internals.

  def poolResource: Resource[IO, Resource[IO, Session[IO]]] =
    Session.pooled(
      host     = "localhost",
      port     = 5432,
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
      max      = 3,
    )

  test("describe cache should be shared across sessions from the same pool") {
    poolResource.use { p =>
      p.use { s1 =>
        p.use { s2 =>
          assert("sessions should be different", s1 ne s2) *>
          assert("caches should be eq", s1.describeCache eq s2.describeCache)
        }
      }
    }
  }

  test("describe cache should be not shared across sessions from different pools") {
    (poolResource, poolResource).tupled.use { case (p1, p2) =>
      p1.use { s1 =>
        p2.use { s2 =>
          assert("sessions should be different", s1 ne s2) *>
          assert("caches should be different", s1.describeCache ne s2.describeCache)
        }
      }
    }
  }


  // Commands

  sessionTest("command should not be cached before `prepare.use`") { s =>
    val cmd = sql"commit".command
    for {
      c <- s.describeCache.commandCache.containsKey(cmd)
      _ <- assertEqual("should not be in cache", c, false)
    } yield "ok"
  }

  sessionTest("command should not be cached if `prepare` fails") { s =>
    val cmd = sql"foo".command
    for {
      _ <- s.prepare(cmd).flatMap(_ => IO.unit).assertFailsWith[PostgresErrorException]
      c <- s.describeCache.commandCache.containsKey(cmd)
      _ <- assertEqual("should not be in cache", c, false)
    } yield "ok"
  }

  sessionTest("command should be cached after `prepare`" ) { s =>
    val cmd = sql"commit".command
    for {
      _ <- s.prepare(cmd).flatMap(_ => IO.unit)
      c <- s.describeCache.commandCache.containsKey(cmd)
      _ <- assertEqual("should be in cache", c, true)
    } yield "ok"
  }

  val runs = 100
  // This should not fail 
  pooledTest("portal1 - concurrent portal with normal flatMap") { pool =>
    import scala.concurrent.duration._
    def cmd(idx: Int): Command[String *: Int *: EmptyTuple] = sql"INSERT INTO scalars VALUES (${text.varchar}, ${int4}) -- #${idx.toString}".command
    1.to(runs).toList.parTraverse{ idx =>
      pool.use { s =>
        s.transaction.use { _ =>
          s.prepare(cmd(idx)).flatMap(_.execute((s"name$idx", idx))).flatMap (_ => 
            IO.sleep(1.second) >> s.prepare(cmd(idx)).flatMap(_.execute((s"name$idx", idx))).void
          )
        }
      }
    } 
  }
  
  // This should not fail 
  pooledTest("portal2 - concurrent portal with map / identity *inside tx*") { pool =>
    import scala.concurrent.duration._
    def cmd(idx: Int): Command[String *: Int *: EmptyTuple] = sql"INSERT INTO scalars VALUES (${text.varchar}, ${int4}) -- #${idx.toString}".command
    1.to(runs).toList.parTraverse{ idx =>
      pool.use { s =>
        s.transaction.use { _ =>
          s.prepare(cmd(idx)).flatMap(_.execute((s"name$idx", idx))).map (_ => 
            IO.sleep(1.second) >> s.prepare(cmd(idx)).flatMap(_.execute((s"name$idx", idx))).void
          ).flatMap(identity)
        }
      }
    } 
  }

  // This will fail if run enough 
  pooledTest("portal3 - concurrent portal with map / identity *outside tx*") { pool =>
    import scala.concurrent.duration._
    def cmd(idx: Int): Command[String *: Int *: EmptyTuple] = sql"INSERT INTO scalars VALUES (${text.varchar}, ${int4}) -- #${idx.toString}".command
    1.to(runs).toList.parTraverse{ idx =>
      pool.use { s =>
        s.transaction.use { _ =>
          s.prepare(cmd(idx)).flatMap(_.execute((s"name$idx", idx))).map (_ => 
            IO.sleep(1.second) >> s.prepare(cmd(idx)).flatMap(_.execute((s"name$idx", idx))).void
          )
        }
      }.flatMap(identity)
    }
  }

  sessionTest("command should not be cached after cache is cleared") { s =>
    val cmd = sql"commit".command
    for {
      _ <- s.prepare(cmd).flatMap(_ => IO.unit)
      c <- s.describeCache.commandCache.containsKey(cmd)
      _ <- assertEqual("should be in cache", c, true)
      _ <- s.describeCache.commandCache.clear
      c <- s.describeCache.commandCache.containsKey(cmd)
      _ <- assertEqual("should not be in cache", c, false)
    } yield "ok"
  }

  // Queries

  sessionTest("query should not be cached before `prepare`") { s =>
    val qry = sql"select 1".query(int4)
    for {
      c <- s.describeCache.queryCache.containsKey(qry)
      _ <- assertEqual("should not be in cache", c, false)
    } yield "ok"
  }

  sessionTest("query should not be cached if `prepare` fails") { s =>
    val qry = sql"foo".query(int4)
    for {
      _ <- s.prepare(qry).flatMap(_ => IO.unit).assertFailsWith[PostgresErrorException]
      c <- s.describeCache.commandCache.containsKey(qry)
      _ <- assertEqual("should not be in cache", c, false)
    } yield "ok"
  }

  sessionTest("query should be cached after `prepare`" ) { s =>
    val qry = sql"select 1".query(int4)
    for {
      _ <- s.prepare(qry).flatMap(_ => IO.unit)
      c <- s.describeCache.queryCache.containsKey(qry)
      _ <- assertEqual("should be in cache", c, true)
    } yield "ok"
  }

  sessionTest("query should not be cached after cache is cleared") { s =>
    val qry = sql"select 1".query(int4)
    for {
      _ <- s.prepare(qry).flatMap(_ => IO.unit)
      c <- s.describeCache.queryCache.containsKey(qry)
      _ <- assertEqual("should be in cache", c, true)
      _ <- s.describeCache.queryCache.clear
      c <- s.describeCache.queryCache.containsKey(qry)
      _ <- assertEqual("should not be in cache", c, false)
    } yield "ok"
  }

}
