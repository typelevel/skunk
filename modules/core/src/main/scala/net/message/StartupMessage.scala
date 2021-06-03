// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec._
import scodec.codecs._

// TODO: SUPPORT OTHER PARAMETERS
case class StartupMessage(
  user: String,
  database: String,
  parameters: Map[String, String]
) extends UntaggedFrontendMessage {

  def encodeBody = StartupMessage
    .encoder(parameters)
    .encode(this)

  // HACK: we will take a plist eventually
  val properties: Map[String, String] =
    Map("user" -> user, "database" -> database)

}

object StartupMessage {

  def encoder(parameters: Map[String, String]): Encoder[StartupMessage] = {

    def pair(key: String): Codec[String] =
      utf8z.applied(key) ~> utf8z

    val version: Codec[Unit] =
      int32.applied(196608)

    // After user and database we have a null-terminated list of fixed key-value pairs, which
    // specify connection properties that affect serialization and are REQUIRED by Skunk.
    val tail: Codec[Unit] =
      parameters.foldRight(byte.applied(0)) { case ((k, v), e) => pair(k).applied(v) <~ e}

    (version.asEncoder, pair("user").asEncoder, pair("database").asEncoder, tail.asEncoder)
      .contramapN(m => ((), m.user, m.database, ()))

  }

}
