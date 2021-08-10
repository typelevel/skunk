// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package test

import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import cats.Eq

class QueryTest extends SkunkTest{

    case class Number(value: Int)
    implicit val numberEq: Eq[Number] = Eq.by(_.value)

    sessionTest("map") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).map(_.toString)).use { ps =>
            for {
                n <- ps.unique(123)
                _ <- assertEqual("123", n, "123")
            } yield "ok"
        }
    }

    sessionTest("gmap") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).gmap[Number]).use { ps =>
            for {
                n <- ps.unique(123)
                _ <- assertEqual("123", n, Number(123))
            } yield "ok"
        }
    }

    sessionTest("contramap") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).contramap[String](_.toInt)).use { ps =>
            for {
                n <- ps.unique("123")
                _ <- assertEqual("123", n, 123)
            } yield "ok"
        }
    }

    sessionTest("gcontramap") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).gcontramap[Number]).use { ps =>
            for {
                n <- ps.unique(Number(123))
                _ <- assertEqual("123", n, 123)
            } yield "ok"
        }
    }


}
