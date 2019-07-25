// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.implicits._
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import skunk.data.Type

trait TemporalCodecs {

  private def temporal[A](format: String, parse: (String, DateTimeFormatter) => A, tpe: Type): Codec[A] = {
    val fmt = DateTimeFormatter.ofPattern(format)
    Codec.simple(
      a => format.format(a),
      s => Either.catchOnly[DateTimeParseException](parse(s, fmt)).leftMap(_.toString),
      tpe
    )
  }

  val date: Codec[LocalDate] =
    temporal("yyyy-MM-dd", LocalDate.parse, Type.date)

  val time: Codec[LocalTime] =
    temporal("HH:mm:ss.SSSSSS", LocalTime.parse, Type.time)

  // format: off
  def time(precision: Int): Codec[LocalTime] =
    precision match {
      case 0 => temporal("HH:mm:ss",        LocalTime.parse, Type.time(0))
      case 1 => temporal("HH:mm:ss.S",      LocalTime.parse, Type.time(1))
      case 2 => temporal("HH:mm:ss.SS",     LocalTime.parse, Type.time(2))
      case 3 => temporal("HH:mm:ss.SSS",    LocalTime.parse, Type.time(3))
      case 4 => temporal("HH:mm:ss.SSSS",   LocalTime.parse, Type.time(4))
      case 5 => temporal("HH:mm:ss.SSSSS",  LocalTime.parse, Type.time(5))
      case 6 => temporal("HH:mm:ss.SSSSSS", LocalTime.parse, Type.time(6))
      case _ => throw new IllegalArgumentException(s"time($precision): invalid precision, expected 0-6")
    }
  // format: on

  val timetz: Codec[OffsetTime] =
    temporal("HH:mm:ss.SSSSSSx", OffsetTime.parse, Type.timetz)

  // format: off
  def timetz(precision: Int): Codec[OffsetTime] =
    precision match {
      case 0 => temporal("HH:mm:ssx",        OffsetTime.parse, Type.timetz(0))
      case 1 => temporal("HH:mm:ss.Sx",      OffsetTime.parse, Type.timetz(1))
      case 2 => temporal("HH:mm:ss.SSx",     OffsetTime.parse, Type.timetz(2))
      case 3 => temporal("HH:mm:ss.SSSx",    OffsetTime.parse, Type.timetz(3))
      case 4 => temporal("HH:mm:ss.SSSSx",   OffsetTime.parse, Type.timetz(4))
      case 5 => temporal("HH:mm:ss.SSSSSx",  OffsetTime.parse, Type.timetz(5))
      case 6 => temporal("HH:mm:ss.SSSSSSx", OffsetTime.parse, Type.timetz(6))
      case _ => throw new IllegalArgumentException(s"timetz($precision): invalid precision, expected 0-6")
    }
  // format: on

  val timestamp: Codec[LocalDateTime] =
    temporal("yyyy-MM-dd HH:mm:ss.SSSSSS", LocalDateTime.parse, Type.timestamp)

  // format: off
  def timestamp(precision: Int): Codec[LocalDateTime] =
    precision match {
      case 0 => temporal("yyyy-MM-dd HH:mm:ss",        LocalDateTime.parse, Type.timestamp(0))
      case 1 => temporal("yyyy-MM-dd HH:mm:ss.S",      LocalDateTime.parse, Type.timestamp(1))
      case 2 => temporal("yyyy-MM-dd HH:mm:ss.SS",     LocalDateTime.parse, Type.timestamp(2))
      case 3 => temporal("yyyy-MM-dd HH:mm:ss.SSS",    LocalDateTime.parse, Type.timestamp(3))
      case 4 => temporal("yyyy-MM-dd HH:mm:ss.SSSS",   LocalDateTime.parse, Type.timestamp(4))
      case 5 => temporal("yyyy-MM-dd HH:mm:ss.SSSSS",  LocalDateTime.parse, Type.timestamp(5))
      case 6 => temporal("yyyy-MM-dd HH:mm:ss.SSSSSS", LocalDateTime.parse, Type.timestamp(6))
      case _ => throw new IllegalArgumentException(s"timestamp($precision): invalid precision, expected 0-6")
    }
  // format: on

  val timestamptz: Codec[OffsetDateTime] =
    temporal("yyyy-MM-dd HH:mm:ss.SSSSSSx", OffsetDateTime.parse, Type.timestamptz)

  // format: off
  def timestamptz(precision: Int): Codec[LocalDateTime] =
    precision match {
      case 0 => temporal("yyyy-MM-dd HH:mm:ssx",        LocalDateTime.parse, Type.timestamptz(0))
      case 1 => temporal("yyyy-MM-dd HH:mm:ss.Sx",      LocalDateTime.parse, Type.timestamptz(1))
      case 2 => temporal("yyyy-MM-dd HH:mm:ss.SSx",     LocalDateTime.parse, Type.timestamptz(2))
      case 3 => temporal("yyyy-MM-dd HH:mm:ss.SSSx",    LocalDateTime.parse, Type.timestamptz(3))
      case 4 => temporal("yyyy-MM-dd HH:mm:ss.SSSSx",   LocalDateTime.parse, Type.timestamptz(4))
      case 5 => temporal("yyyy-MM-dd HH:mm:ss.SSSSSx",  LocalDateTime.parse, Type.timestamptz(5))
      case 6 => temporal("yyyy-MM-dd HH:mm:ss.SSSSSSx", LocalDateTime.parse, Type.timestamptz(6))
      case _ => throw new IllegalArgumentException(s"timestamptz($precision): invalid precision, expected 0-6")
    }
  // format: on

  // todo: intervals, which will need a new data type

}

object temporal extends TemporalCodecs
