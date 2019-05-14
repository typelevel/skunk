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

  val time: Codec[LocalTime] = time(6)

  def time(precision: Int): Codec[LocalTime] =
    precision match {
      case 0 => temporal("HH:mm:ss",        LocalTime.parse, Type.time)
      case 1 => temporal("HH:mm:ss.S",      LocalTime.parse, Type.time)
      case 2 => temporal("HH:mm:ss.SS",     LocalTime.parse, Type.time)
      case 3 => temporal("HH:mm:ss.SSS",    LocalTime.parse, Type.time)
      case 4 => temporal("HH:mm:ss.SSSS",   LocalTime.parse, Type.time)
      case 5 => temporal("HH:mm:ss.SSSSS",  LocalTime.parse, Type.time)
      case 6 => temporal("HH:mm:ss.SSSSSS", LocalTime.parse, Type.time)
      case _ => throw new IllegalArgumentException(s"time($precision): invalid precision, expected 0-6")
    }

  val timetz: Codec[OffsetTime] = timetz(6)
  def timetz(precision: Int): Codec[OffsetTime] =
    precision match {
      case 0 => temporal("HH:mm:ssx",        OffsetTime.parse, Type.timetz)
      case 1 => temporal("HH:mm:ss.Sx",      OffsetTime.parse, Type.timetz)
      case 2 => temporal("HH:mm:ss.SSx",     OffsetTime.parse, Type.timetz)
      case 3 => temporal("HH:mm:ss.SSSx",    OffsetTime.parse, Type.timetz)
      case 4 => temporal("HH:mm:ss.SSSSx",   OffsetTime.parse, Type.timetz)
      case 5 => temporal("HH:mm:ss.SSSSSx",  OffsetTime.parse, Type.timetz)
      case 6 => temporal("HH:mm:ss.SSSSSSx", OffsetTime.parse, Type.timetz)
      case _ => throw new IllegalArgumentException(s"timetz($precision): invalid precision, expected 0-6")
    }

  val timestamp: Codec[LocalDateTime] =
    temporal("yyyy-MM-dd HH:mm:ss.SSSSSS", LocalDateTime.parse, Type.timestamp)

  val timestamptz: Codec[OffsetDateTime] =
    temporal("yyyy-MM-dd HH:mm:ss.SSSSSSx", OffsetDateTime.parse, Type.timestamptz)

  // todo: intervals, which will need a new data type

}

object temporal extends TemporalCodecs