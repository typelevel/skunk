// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.Eq
import cats.syntax.all._
import org.typelevel.cats.time.{ offsetdatetimeInstances => _, _ }
import java.time._
import skunk.codec.temporal._
import cats.effect.{IO, Resource}
import skunk._, skunk.implicits._
import scala.concurrent.duration.{ Duration => SDuration }

class TemporalCodecTest extends CodecTest {

  // For these tests consider `OffsetDateTime`s equal if they refer to the same instant in time,
  // rather than distinguishing the same point in time as specified in different timezones, which
  // is the default.
  implicit val offsetDateTimeEq: Eq[OffsetDateTime] =
    Eq.by(_.toInstant)

  // Also, run these tests with the session set to a timezone other than UTC. Our test instance is
  // set to UTC, which masks the error reported at https://github.com/tpolecat/skunk/issues/313.
  override def session(readTimeout: SDuration): Resource[IO,Session[IO]] =
    super.session(readTimeout).evalTap(s => s.execute(sql"SET TIME ZONE +3".command))

  // Date
  val dates: List[LocalDate] =
    List(
      LocalDate.of(-4713, 12, 31),  // Earliest date Postgres can store
      LocalDate.of(2019, 6, 17),    // A reasonable date.
      LocalDate.of(256789, 12, 31), // A very distant date
      LocalDate.of(1, 2, 3),        // A date having a year with less than 3 digits
    )

  roundtripTest(date)(dates: _*)

  // Time
  val times: List[LocalTime] =
    List(
      LocalTime.MIN,
      LocalTime.NOON,
      LocalTime.of(5, 23, 58),
      LocalTime.of(17, 23, 58),
      LocalTime.MIDNIGHT,
    )

  roundtripTest(time   )(times :+ LocalTime.MAX.withNano(999999000): _*)
  roundtripTest(time(6))(times :+ LocalTime.MAX.withNano(999999000): _*)
  roundtripTest(time(5))(times :+ LocalTime.MAX.withNano(999990000): _*)
  roundtripTest(time(4))(times :+ LocalTime.MAX.withNano(999900000): _*)
  roundtripTest(time(3))(times :+ LocalTime.MAX.withNano(999000000): _*)
  roundtripTest(time(2))(times :+ LocalTime.MAX.withNano(990000000): _*)
  roundtripTest(time(1))(times :+ LocalTime.MAX.withNano(900000000): _*)
  roundtripTest(time(0))(times :+ LocalTime.MAX.withNano(0): _*)
  decodeFailureTest(time, List("x"))

  // Timestamp
  val dateTimes: List[LocalDateTime] =
    (dates, times).mapN(_ atTime _)

  roundtripTest(timestamp   )(dateTimes: _*)
  roundtripTest(timestamp(6))(dateTimes: _*)
  roundtripTest(timestamp(5))(dateTimes: _*)
  roundtripTest(timestamp(4))(dateTimes: _*)
  roundtripTest(timestamp(3))(dateTimes: _*)
  roundtripTest(timestamp(2))(dateTimes: _*)
  roundtripTest(timestamp(1))(dateTimes: _*)
  roundtripTest(timestamp(0))(dateTimes: _*)
  decodeFailureTest(timestamp, List("x"))

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

  roundtripTest(timetz   )(offsetTimes: _*)
  roundtripTest(timetz(6))(offsetTimes: _*)
  roundtripTest(timetz(5))(offsetTimes: _*)
  roundtripTest(timetz(4))(offsetTimes: _*)
  roundtripTest(timetz(3))(offsetTimes: _*)
  roundtripTest(timetz(2))(offsetTimes: _*)
  roundtripTest(timetz(1))(offsetTimes: _*)
  roundtripTest(timetz(0))(offsetTimes: _*)
  decodeFailureTest(timetz, List("x"))

  // Timestamp with offset
  val offsetDateTimes: List[OffsetDateTime] =
    (dateTimes, offsets).mapN(_ atOffset _)

  roundtripTest(timestamptz   )(offsetDateTimes: _*)
  roundtripTest(timestamptz(6))(offsetDateTimes: _*)
  roundtripTest(timestamptz(5))(offsetDateTimes: _*)
  roundtripTest(timestamptz(4))(offsetDateTimes: _*)
  roundtripTest(timestamptz(3))(offsetDateTimes: _*)
  roundtripTest(timestamptz(2))(offsetDateTimes: _*)
  roundtripTest(timestamptz(1))(offsetDateTimes: _*)
  roundtripTest(timestamptz(0))(offsetDateTimes: _*)
  decodeFailureTest(timestamptz, List("x"))

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

  roundtripTest(interval)(intervals: _*)
  decodeFailureTest(interval, List("x"))

}
