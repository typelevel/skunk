// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.syntax.all._
import skunk.exception.PostgresErrorException
import skunk.exception.UnsupportedAuthenticationSchemeException
import skunk.exception.UnsupportedSASLMechanismsException
import skunk.net.message._

class StartupSimTest extends SimTest {

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

  test(s"unsupported sasl mechanism") {
    val sim = flatExpect { case StartupMessage(_, _) => send(AuthenticationSASL(List("Foo", "Bar"))) *> halt }
    simSession(sim,"bob", "db", None)
      .assertFailsWith[UnsupportedSASLMechanismsException]
      .as("ok")
  }
}
