// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.issue

import cats.implicits._
import ffstest.FTest
import java.util.Locale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import skunk.codec.temporal.timestamptz
import java.time.OffsetDateTime
import java.time.ZoneOffset

// https://github.com/tpolecat/skunk/issues/231
case object Test231 extends FTest {

  val es_CO: Locale =
    Locale.getAvailableLocales().find(_.toString == "es_CO").getOrElse(sys.error("Cannot find es_CO locale."))

  val ts: OffsetDateTime =
    OffsetDateTime.of(LocalDateTime.of(2020, 1, 1, 12, 30, 0), ZoneOffset.ofHours(6))

  def inColombia[A](a: => A): A = {
    val prev = Locale.getDefault()
    try { Locale.setDefault(es_CO); a } finally Locale.setDefault(prev)
  }

  test("'G' formatter expands to 'anno Dómini' if the Locale is set to es_CO") {
    inColombia {
      assertEqual("era", DateTimeFormatter.ofPattern("G").format(ts), "anno Dómini")
    }
  }

  test("timestamptz formatter is independent of Locale") {
    inColombia {
      assertEqual("timestamptz", timestamptz.encode(ts).head.get, "2020-01-01 12:30:00+06 AD")
    }
  }

}