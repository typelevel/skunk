// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import skunk._
import tests.SkunkTest
import skunk.implicits._
import java.time.Instant
import skunk.codec.all._
import java.time.ZoneOffset
import cats.kernel.Eq

// N.B. TemporalCodecTest now runs in UTC+3 as well, which is a more general test. So this case
// is unnecessary strictly speaking but is here for reference.

// https://github.com/tpolecat/skunk/issues/313
abstract class Test313 extends SkunkTest {

  val instantCodec: Codec[Instant] =
    timestamptz.imap(_.toInstant)(_.atOffset(ZoneOffset.UTC))

  implicit val EqInstant: Eq[Instant] =
    Eq.by(_.toEpochMilli)

  val instant =
    Instant.parse("2020-11-23T10:24:31.000Z")

  sessionTest("issue/313") { s =>
    for {
      _  <- s.execute(sql"SET TIME ZONE +3".command)
      i  <- s.prepare(sql"SELECT $instantCodec".query(instantCodec)).use(_.unique(instant))
      _  <- assertEqual("instant roundtrip via timestamptz", instant, i)
    } yield "ok"
  }

}
