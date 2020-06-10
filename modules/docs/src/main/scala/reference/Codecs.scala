// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package reference

import skunk.Session
import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.Resource
import skunk.Codec

object Codecs extends IOApp {

  import cats.effect.IO

  //#codecs-a
  import java.time.{YearMonth, LocalDate}
  import skunk.Encoder
  import skunk.codec.all._
  import skunk.implicits._
  import natchez.Trace.Implicits.noop

  final case class User(name: String, dateOfBirth: LocalDate)

  val yearMonthEncoder: Encoder[YearMonth] = 
    text.contramap[YearMonth](yearMonth => yearMonth.toString)

  val manualUserEncoder: Encoder[User] = 
    (text ~ date).contramap { case User(name, birthday) => name ~ birthday }
  //#codecs-a

  //#codecs-b
  val userEncoder: Encoder[User] =
    (text ~ date).gcontramap[User]
  //#codecs-b

  //#codecs-c
  import skunk.Decoder

  val yearMonthDecoder: Decoder[YearMonth] =
    text.map(stringYearMonth => YearMonth.parse(stringYearMonth))
  //#codecs-c

  //#codecs-d
  val userDecoder: Decoder[User] =
    (text ~ date).gmap[User]
  //#codecs-d

  //#codecs-e
  val yearMonthCodec: Codec[YearMonth] =
    text.imap(str => YearMonth.parse(str))(yearMonth => yearMonth.toString)
  //#codecs-e

  //#codecs-f
  val userCodec: Codec[User] =
    (text ~ date).gimap[User]
  //#codecs-f

  //#codecs-g
  val ids = List(1, 14, 42)
  val idEncoder = int4.list(ids.length).values

  sql"SELECT name FROM user WHERE id IN $idEncoder"
  // will result in: SELECT name FROM user WHERE id IN (1, 14, 42)
  //#codecs-g

  val session: Resource[IO, Session[IO]] = {
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )
  }

  def run(args: List[String]): IO[ExitCode] =
    IO.pure(ExitCode.Success)

}