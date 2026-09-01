// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk._
import skunk.codec.all._
import skunk.implicits._
import cats.Eq
import cats.effect.IO
import scala.concurrent.duration._
import skunk.data.Type
import skunk.exception.{ DecodeException, SkunkException }

class QueryTest extends SkunkTest {

  case class Number(value: Int)
  implicit val numberEq: Eq[Number] = Eq.by(_.value)

  sessionTest("unique") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i) limit 1".query(int4)
    for {
      n <- s.unique(query)((123, 456, 789))
      _ <- assertEqual("123", n, 123)
    } yield "ok"
  }

  sessionTest("option - Some") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i) limit 1".query(int4)
    for {
      n <- s.option(query)((123, 456, 789))
      _ <- assertEqual("123", n, Some(123))
    } yield "ok"
  }

  sessionTest("option - None") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i) limit 0".query(int4)
    for {
      n <- s.option(query)((123, 456, 789))
      _ <- assertEqual("123", n, None.asInstanceOf[Option[Int]])
    } yield "ok"
  }

  // A decode failure leaves the server happy and the portal open, so skunk closes it inline. What
  // is checkable from here is that doing so is protocol-legal and leaves the session usable; that
  // the Close is sent at all is pinned by ExchangeCountTest.
  sessionTest("decode failure closes its portal and leaves the session synchronized") { s =>
    val bad = sql"select null::varchar where $int4 = 1".query(varchar)
    for {
      _ <- s.unique(bad)(1).assertFailsWith[DecodeException[IO, _, _]]
      n <- s.unique(sql"select 1".query(int4))
      _ <- assertEqual("session still usable", n, 1)
      _ <- s.transaction.use { _ =>
             s.unique(bad)(1).assertFailsWith[DecodeException[IO, _, _]]
           }
      m <- s.unique(sql"select 2".query(int4))
      _ <- assertEqual("session still usable after a transaction", m, 2)
      _ <- s.assertHealthy
    } yield "ok"
  }

  // Sync does not end an explicit transaction, so the portal outlives it and must still be closed
  // -- the branch the ordinary case never takes. CommandTest covers the command side.
  sessionTest("parameterized queries inside a transaction") { s =>
    val many = sql"select * from (values ($int4), ($int4), ($int4)) as t(i)".query(int4)
    val one  = sql"select $int4".query(int4)
    s.transaction.use { _ =>
      for {
        as <- s.execute(many)((123, 456, 789))
        _  <- assertEqual("execute", as, List(123, 456, 789))
        b  <- s.unique(one)(42)
        _  <- assertEqual("unique", b, 42)
        c  <- s.option(one)(7)
        _  <- assertEqual("option", c, Some(7))
      } yield ()
    } >> s.assertHealthy.as("ok")
  }

  // option and unique ask for 2 rows to tell whether more exist. When more do, the portal suspends
  // rather than completing, and the ReadyForQuery still has to be read or the next operation picks
  // it up. So what matters is not that these fail, but that the session works afterwards.
  sessionTest("option - more rows than asked for leaves the session synchronized") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i)".query(int4)
    for {
      _ <- s.option(query)((123, 456, 789)).assertFailsWith[SkunkException]
      n <- s.unique(sql"select 1".query(int4))
      _ <- assertEqual("session still usable", n, 1)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("unique - more rows than asked for leaves the session synchronized") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i)".query(int4)
    for {
      _ <- s.unique(query)((123, 456, 789)).assertFailsWith[SkunkException]
      n <- s.unique(sql"select 1".query(int4))
      _ <- assertEqual("session still usable", n, 1)
      _ <- s.assertHealthy
    } yield "ok"
  }

  sessionTest("list") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i)".query(int4)
    for {
      n <- fs2.Stream(
        1 *: 2 *: 3 *: EmptyTuple,
        4 *: 5 *: 6 *: EmptyTuple,
        7 *: 8 *: 9 *: EmptyTuple
      ).through(s.pipe(query, 10)).compile.toList
      _ <- assertEqual("123", n, List(1, 2, 3, 4, 5, 6, 7, 8, 9))
    } yield "ok"
  }

  sessionTest("list") { s =>
    val query = sql"select * from (values ($int4), ($int4), ($int4)) as t(i)".query(int4)
    for {
      n <- s.execute(query)((123, 456, 789))
      _ <- assertEqual("123", n, List(123, 456, 789))
    } yield "ok"
  }

  sessionTest("map") { s =>
    val f = sql"select $int4"
    s.prepare(f.query(int4).map(_.toString)).flatMap { ps =>
      for {
          n <- ps.unique(123)
          _ <- assertEqual("123", n, "123")
      } yield "ok"
    }
  }

  sessionTest("as") { s =>
    val f = sql"select $int4"
    s.prepare(f.query(int4).to[Number]).flatMap { ps =>
      for {
        n <- ps.unique(123)
        _ <- assertEqual("123", n, Number(123))
      } yield "ok"
    }
  }

  sessionTest("contramap") { s =>
    val f = sql"select $int4"
    s.prepare(f.query(int4).contramap[String](_.toInt)).flatMap { ps =>
      for {
        n <- ps.unique("123")
        _ <- assertEqual("123", n, 123)
      } yield "ok"
    }
  }

  sessionTest("gcontramap") { s =>
    val f = sql"select $int4"
    s.prepare(f.query(int4).contrato[Number]).flatMap { ps =>
      for {
        n <- ps.unique(Number(123))
        _ <- assertEqual("123", n, 123)
      } yield "ok"
    }
  }

  val void: Decoder[skunk.Void] = new Decoder[skunk.Void] {
    def types: List[Type] = List(Type.void)
    def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, skunk.Void] = Right(skunk.Void)
  }


  pooledTest("timeout", readTimeout = 2.seconds) { getS =>
    val f = sql"select pg_sleep($int4)"
    def getErr[X]: Either[Throwable, X] => Option[String] = _.swap.toOption.collect {
      case e: java.util.concurrent.TimeoutException => e.getMessage()
    }
    for {
      sessionBroken <- getS.use { s =>
        s.prepare(f.query(void)).flatMap { ps =>
          for {
            ret <- ps.unique(8).attempt
            _ <- assertEqual("timeout error check", getErr(ret), Option("2 seconds"))
          } yield "ok"
        }
      }.attempt
      _ <- assertEqual("timeout error check", getErr(sessionBroken), Option("2 seconds"))
      _ <- getS.use { s =>
        s.prepare(f.query(void)).flatMap { ps =>
          for {
            ret <- ps.unique(1).attempt
            _ <- assertEqual("timeout error ok", ret.isRight, true)
          } yield "ok"
        }
      }
    } yield "ok"
  }

  sessionTest("explain query") { s =>
    for {
      c <- s.unique(sql"""EXPLAIN SELECT * FROM city""".query(skunk.codec.all.text))
      _ <- assert("completion", c.startsWith("Seq Scan on city"))
      _ <- s.assertHealthy
    } yield "ok"
  }
}
