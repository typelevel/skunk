// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.Eq
import cats.implicits._
import io.chrisdavenport.cats.time.{ offsetdatetimeInstances => _, _ }
import java.time._
import skunk.codec.temporal._

case object TemporalCodecTest extends CodecTest {

  // For these tests consider `OffsetDateTime`s equal if they refer to the same instant in time,
  // rather than distinguishing the same point in time as specified in different timezones, which
  // is the default.
  implicit val offsetDateTimeEq: Eq[OffsetDateTime] =
    Eq.by(_.toInstant)

  // Date
  val dates: List[LocalDate] =
    List(
      LocalDate.of(-4713, 12, 31),  // Earliest date Postgres can store
      LocalDate.of(2019, 6, 17),    // A reasonable date.
      LocalDate.of(256789, 12, 31), // A very distant date
    )

  codecTest(date)(dates: _*)

  // Time
  val times: List[LocalTime] =
    List(
      LocalTime.MIN,
      LocalTime.NOON,
      LocalTime.of(5, 23, 58),
      LocalTime.of(17, 23, 58),
      LocalTime.MIDNIGHT,
    )

  codecTest(time   )(times :+ LocalTime.MAX.withNano(999999000): _*)
  codecTest(time(6))(times :+ LocalTime.MAX.withNano(999999000): _*)
  codecTest(time(5))(times :+ LocalTime.MAX.withNano(999990000): _*)
  codecTest(time(4))(times :+ LocalTime.MAX.withNano(999900000): _*)
  codecTest(time(3))(times :+ LocalTime.MAX.withNano(999000000): _*)
  codecTest(time(2))(times :+ LocalTime.MAX.withNano(990000000): _*)
  codecTest(time(1))(times :+ LocalTime.MAX.withNano(900000000): _*)
  codecTest(time(0))(times :+ LocalTime.MAX.withNano(0): _*)

  // Timestamp
  val dateTimes: List[LocalDateTime] =
    (dates, times).mapN(_ atTime _)

  codecTest(timestamp   )(dateTimes: _*)
  codecTest(timestamp(6))(dateTimes: _*)
  codecTest(timestamp(5))(dateTimes: _*)
  codecTest(timestamp(4))(dateTimes: _*)
  codecTest(timestamp(3))(dateTimes: _*)
  codecTest(timestamp(2))(dateTimes: _*)
  codecTest(timestamp(1))(dateTimes: _*)
  codecTest(timestamp(0))(dateTimes: _*)

  // Time with offset
  val offsets: List[ZoneOffset] =
    List(
      ZoneOffset.ofHours(-13),
      ZoneOffset.UTC,
      ZoneOffset.ofHours(15),
      ZoneOffset.ofHoursMinutes(4, 30),
    )

  val offsetTimes: List[OffsetTime] =
    (times, offsets).mapN(_ atOffset _)

  codecTest(timetz   )(offsetTimes: _*)
  codecTest(timetz(6))(offsetTimes: _*)
  codecTest(timetz(5))(offsetTimes: _*)
  codecTest(timetz(4))(offsetTimes: _*)
  codecTest(timetz(3))(offsetTimes: _*)
  codecTest(timetz(2))(offsetTimes: _*)
  codecTest(timetz(1))(offsetTimes: _*)
  codecTest(timetz(0))(offsetTimes: _*)

  // Timestamp with offset
  val offsetDateTimes: List[OffsetDateTime] =
    (dateTimes, offsets).mapN(_ atOffset _)

  codecTest(timestamptz   )(offsetDateTimes: _*)
  codecTest(timestamptz(6))(offsetDateTimes: _*)
  codecTest(timestamptz(5))(offsetDateTimes: _*)
  codecTest(timestamptz(4))(offsetDateTimes: _*)
  codecTest(timestamptz(3))(offsetDateTimes: _*)
  codecTest(timestamptz(2))(offsetDateTimes: _*)
  codecTest(timestamptz(1))(offsetDateTimes: _*)
  codecTest(timestamptz(0))(offsetDateTimes: _*)

  // Interval
  val intervals: List[Duration] =
    List(
      Duration.ZERO,
      Duration.ofDays(-999999),
      Duration.ofDays(9999999),
      Duration
        .ofDays(9999)
        .plusHours(21)
        .plusMinutes(55)
        .plusSeconds(55)
        .plusMillis(555)
        .plusNanos(555000),
    )

  codecTest(interval)(intervals: _*)

}