// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec._
import scodec.codecs._

// TODO: SUPPORT OTHER PARAMETERS
case class StartupMessage(user: String, database: String) extends UntaggedFrontendMessage {

  def encodeBody = StartupMessage.encoder.encode(this)

  // HACK: we will take a plist eventually
  val properties: Map[String, String] =
    Map("user" -> user, "database" -> database)

}

object StartupMessage {

  val ConnectionProperties: List[(String, String)] =
    List(
      "client_min_messages" -> "WARNING",
      "DateStyle"           -> "ISO, MDY",
      "IntervalStyle"       -> "iso_8601",
      "client_encoding"     -> "UTF8",
    )

  val encoder: Encoder[StartupMessage] = {

    def pair(key: String): Codec[String] =
      utf8z.applied(key) ~> utf8z

    val version: Codec[Unit] =
      int32.applied(196608)

    // After user and database we have a null-terminated list of fixed key-value pairs, which
    // specify connection properties that affect serialization and are REQUIRED by Skunk.
    val tail: Codec[Unit] =
      ConnectionProperties.foldRight(byte.applied(0)) { case ((k, v), e) => pair(k).applied(v) <~ e}

    (version ~> pair("user") ~ pair("database") <~ tail)
      .asEncoder
      .contramap(m => m.user ~ m.database)

  }

}