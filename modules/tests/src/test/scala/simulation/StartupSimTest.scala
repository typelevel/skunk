// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.implicits._
import skunk.exception.PostgresErrorException
import skunk.exception.UnsupportedAuthenticationSchemeException
import skunk.net.message._

case object StartupSimTest extends SimTest {

  test("immediate server error") {
    val sim = flatExpect { case StartupMessage(_, _) => error("Nope") *> halt }
    for {
      e <- simSession(sim, "Bob", "db").assertFailsWith[PostgresErrorException]
      _ <- assertEqual("message", e.message, "Nope.")
    } yield ("ok")

  }

  List(
    AuthenticationCleartextPassword,
    AuthenticationGSS,
    AuthenticationKerberosV5,
    AuthenticationSASL(List("Foo", "Bar")),
    AuthenticationSCMCredential,
    AuthenticationSSPI,
  ).foreach { msg =>
    test(s"unsupported authentication scheme ($msg)") {
      val sim = flatExpect { case StartupMessage(_, _) => send(msg) *> halt }
      simSession(sim,"bob", "db", None)
        .assertFailsWith[UnsupportedAuthenticationSchemeException]
        .as("ok")
    }
  }

}
