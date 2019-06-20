// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import io.chrisdavenport.cats.time.{offsetdatetimeInstances => _, _}
import java.time.LocalDate
import java.time.LocalTime

import skunk.codec.temporal._
import java.time.ZoneOffset
import cats.kernel.Eq
import java.time.OffsetDateTime
import java.time.Duration

case object TemporalCodecTest extends CodecTest {

  // instance from cats-time takes only local datetimes into consideration
  implicit val offsetDateTimeEq = new Eq[OffsetDateTime] {
    def eqv(x: OffsetDateTime, y: OffsetDateTime): Boolean =
      x.toInstant() == y.toInstant()
  }

  // Date
  val earliestDatePostgresCanStore = LocalDate.of(-4713, 12, 31)
  val veryBigPostgresDate = LocalDate.of(256789, 12, 31)
  val dates = List(earliestDatePostgresCanStore, LocalDate.of(2019, 6, 17), veryBigPostgresDate)

  codecTest(date)(dates: _*)


  // Time
  val times = List(LocalTime.MIN, LocalTime.NOON, LocalTime.of(5, 23, 58), LocalTime.of(17, 23, 58), LocalTime.MIDNIGHT)

  codecTest(time)(times :+ LocalTime.MAX.withNano(999999000): _*)
  codecTest(time(6))(times :+ LocalTime.MAX.withNano(999999000): _*)
  codecTest(time(5))(times :+ LocalTime.MAX.withNano(999990000): _*)
  codecTest(time(4))(times :+ LocalTime.MAX.withNano(999900000): _*)
  codecTest(time(3))(times :+ LocalTime.MAX.withNano(999000000): _*)
  codecTest(time(2))(times :+ LocalTime.MAX.withNano(990000000): _*)
  codecTest(time(1))(times :+ LocalTime.MAX.withNano(900000000): _*)
  codecTest(time(0))(times :+ LocalTime.MAX.withNano(0): _*)


  // Timestamp
  val dateTimes = for { 
    date <- dates
    time <- times
  } yield date.atTime(time)

  codecTest(timestamp)(dateTimes: _*)
  codecTest(timestamp(6))(dateTimes: _*)
  codecTest(timestamp(5))(dateTimes: _*)
  codecTest(timestamp(4))(dateTimes: _*)
  codecTest(timestamp(3))(dateTimes: _*)
  codecTest(timestamp(2))(dateTimes: _*)
  codecTest(timestamp(1))(dateTimes: _*)
  codecTest(timestamp(0))(dateTimes: _*)

  // Time with offset
  val offsets = List(ZoneOffset.ofHours(-13), ZoneOffset.UTC, ZoneOffset.ofHours(15), ZoneOffset.ofHoursMinutes(4, 30))
  val offsetTimes = for {
    time   <- times
    offset <- offsets
  } yield time.atOffset(offset)

  codecTest(timetz)(offsetTimes: _*)
  codecTest(timetz(6))(offsetTimes: _*)
  codecTest(timetz(5))(offsetTimes: _*)
  codecTest(timetz(4))(offsetTimes: _*)
  codecTest(timetz(3))(offsetTimes: _*)
  codecTest(timetz(2))(offsetTimes: _*)
  codecTest(timetz(1))(offsetTimes: _*)
  codecTest(timetz(0))(offsetTimes: _*)

  // Timestamp with offset
  val offsetDateTimes = for {
    dateTime <- dateTimes
    offset   <- offsets
  } yield dateTime.atOffset(offset)

  codecTest(timestamptz)(offsetDateTimes: _*)
  codecTest(timestamptz(6))(offsetDateTimes: _*)
  codecTest(timestamptz(5))(offsetDateTimes: _*)
  codecTest(timestamptz(4))(offsetDateTimes: _*)
  codecTest(timestamptz(3))(offsetDateTimes: _*)
  codecTest(timestamptz(2))(offsetDateTimes: _*)
  codecTest(timestamptz(1))(offsetDateTimes: _*)
  codecTest(timestamptz(0))(offsetDateTimes: _*)

  // Interval
  val intervals = List(Duration.ZERO, Duration.ofDays(-999999), Duration.ofDays(9999999),
                       Duration.ofDays(9999).plusHours(21).plusMinutes(55).plusSeconds(55).plusMillis(555).plusNanos(555000))

  codecTest(interval)(intervals: _*)

}