// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import skunk.exception.DecodeException
import cats.effect.IO

// https://github.com/tpolecat/skunk/issues/129
case object Test129 extends SkunkTest {

  case class Country(name: String, code: String)
  case class City(name: String, district: String)

  val q: Query[Void, (Country, Option[City])] =
    sql"""
      SELECT c.name, c.code, k.name, k.district
      FROM country c
      LEFT OUTER JOIN city k
      ON c.capital = k.id
      ORDER BY c.code DESC
    """.query(varchar ~ bpchar(3) ~ varchar.opt ~ varchar.opt)
       .map {
          case a ~ b ~ Some(c) ~ Some(d) => (Country(a, b), Some(City(c, d)))
          // the incomplete match results in a `MatchError` which is expected, but it also
          // reports a ProtocolError, which shouldn't happen.
        }

  sessionTest("issue/129") { s =>
    for {
      _ <- s.execute(q).assertFailsWith[DecodeException[IO,_,_]]
      _ <- s.assertHealthy
    } yield "ok"
  }

}