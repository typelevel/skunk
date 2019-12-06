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
import java.time.temporal.ChronoField._
import skunk.data.Type
import java.time.temporal.TemporalAccessor
import java.time.format.DateTimeFormatterBuilder
import java.time.Duration

trait TemporalCodecs {

  private def temporal[A <: TemporalAccessor](formatter: DateTimeFormatter, parse: (String, DateTimeFormatter) => A, tpe: Type): Codec[A] =
    Codec.simple(
      a => formatter.format(a),
      s => Either.catchOnly[DateTimeParseException](parse(s, formatter)).leftMap(_.toString),
      tpe
    )

  private def timeFormatter(precision: Int): DateTimeFormatter = {
    val requiredPart = new DateTimeFormatterBuilder()
      .appendValue(HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(MINUTE_OF_HOUR, 2)
      .appendLiteral(':')
      .appendValue(SECOND_OF_MINUTE, 2)

    if(precision > 0) {
      requiredPart.optionalStart()
        .appendFraction(NANO_OF_SECOND, 0, precision, true)
        .optionalEnd()
        .toFormatter()
    } else {
      requiredPart.toFormatter()
    }
  }

  // Postgres does not understand dates with minus sign at the beggining
  // Instead we need to create custom formatters and append BC/AD after the date
  private val localDateFormatterWithoutEra =
    new DateTimeFormatterBuilder()
      .appendValue(YEAR_OF_ERA)
      .appendLiteral('-')
      .appendValue(MONTH_OF_YEAR, 2)
      .appendLiteral('-')
      .appendValue(DAY_OF_MONTH, 2)
      .toFormatter()

  private val eraFormatter = DateTimeFormatter.ofPattern(" G")

  private val localDateFormatter =
    new DateTimeFormatterBuilder()
      .append(localDateFormatterWithoutEra)
      .appendOptional(eraFormatter)
      .toFormatter

  private def localDateTimeFormatter(precision: Int) =
    new DateTimeFormatterBuilder()
      .append(localDateFormatterWithoutEra)
      .appendLiteral(' ')
      .append(timeFormatter(precision))
      .appendOptional(eraFormatter)
      .toFormatter()

  // If the offset is only hours, postgres will return time like this: "12:40:50+13"
  // We need to provide a custom offset format to parse this with DateTimeFormatter
  private def offsetTimeFormatter(precision: Int) =
    new DateTimeFormatterBuilder()
      .append(timeFormatter(precision))
      .appendOffset("+HH:mm", "")
      .toFormatter

  private def offsetDateTimeFormatter(precision: Int) =
    new DateTimeFormatterBuilder()
      .append(localDateFormatterWithoutEra)
      .appendLiteral(' ')
      .append(timeFormatter(precision))
      .appendOffset("+HH:mm", "")
      .appendOptional(eraFormatter)
      .toFormatter

  val date: Codec[LocalDate] =
    temporal(localDateFormatter, LocalDate.parse, Type.date)

  val time: Codec[LocalTime] =
    temporal(timeFormatter(6), LocalTime.parse, Type.time)

  def time(precision: Int): Codec[LocalTime] =
    if (precision >= 0 && precision <= 6) {
      temporal(timeFormatter(precision), LocalTime.parse, Type.time(precision))
    } else {
      throw new IllegalArgumentException(s"time($precision): invalid precision, expected 0-6")
    }

  val timetz: Codec[OffsetTime] =
    temporal(offsetTimeFormatter(6), OffsetTime.parse, Type.timetz)

  def timetz(precision: Int): Codec[OffsetTime] =
    if(precision >= 0 && precision <= 6) {
      temporal(offsetTimeFormatter(precision), OffsetTime.parse, Type.timetz(precision))
    } else {
      throw new IllegalArgumentException(s"timetz($precision): invalid precision, expected 0-6")
    }

  val timestamp: Codec[LocalDateTime] =
    temporal(localDateTimeFormatter(6), LocalDateTime.parse, Type.timestamp)

  def timestamp(precision: Int): Codec[LocalDateTime] =
    if(precision >= 0 && precision <= 6) {
      temporal(localDateTimeFormatter(precision), LocalDateTime.parse, Type.timestamp(precision))
    } else {
      throw new IllegalArgumentException(s"timestamp($precision): invalid precision, expected 0-6")
    }

  val timestamptz: Codec[OffsetDateTime] =
    temporal(offsetDateTimeFormatter(6), OffsetDateTime.parse, Type.timestamptz)

  def timestamptz(precision: Int): Codec[OffsetDateTime] =
    if(precision >= 0 && precision <= 6) {
      temporal(offsetDateTimeFormatter(precision), OffsetDateTime.parse, Type.timestamptz(precision))
    } else {
      throw new IllegalArgumentException(s"timestampz($precision): invalid precision, expected 0-6")
    }

  private def intervalCodec(tpe: Type): Codec[Duration] = Codec.simple(
    duration => duration.toString,
    str => Either.catchOnly[DateTimeParseException](Duration.parse(str)).leftMap(_.toString),
    tpe
  )

  val interval: Codec[Duration] = intervalCodec(Type.interval)

  def interval(precision: Int): Codec[Duration] =
    if(precision >= 0 && precision <= 6)
      intervalCodec(Type.interval(precision))
    else
      throw new IllegalArgumentException(s"interval($precision): invalid precision, expected 0-6")

}

object temporal extends TemporalCodecs