// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import skunk.net.message._
import skunk.net.protocol.Startup
import skunk.net.protocol.Exchange
import natchez.Trace.Implicits.noop
import cats.effect.IO
import cats.implicits._
import skunk.exception.UnsupportedAuthenticationSchemeException
import skunk.exception.PostgresErrorException

case object SimulatedStartupTest extends ffstest.FTest {
  import SimulatedMessageSocket._

    test("immediate server error") {

      // A trivial simulator that responds to StartupMessage with an ErrorMessage.
      val sim: Simulator =
        flatExpect {
          case StartupMessage(_, _) =>
            respond(ErrorResponse(Map('M' -> "Nope", 'S' -> "ERROR", 'C' -> "123"))) *>
            halt
        }

      for {
        ex <- Exchange[IO]
        ms <- SimulatedMessageSocket(sim)
        s   = Startup[IO](implicitly, ex, ms, implicitly)
        e  <- s.apply("bob", "db", None).assertFailsWith[PostgresErrorException]
        _  <- assertEqual("message", e.message, "Nope.")
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

      // A trivial simulator that only handles the first exchange of the Startup protocol.
      val sim: Simulator =
        flatExpect {
          case StartupMessage(_, _) =>
            respond(msg) *>
            halt
        }

      for {
        ex <- Exchange[IO]
        ms <- SimulatedMessageSocket(sim)
        s   = Startup[IO](implicitly, ex, ms, implicitly)
        _  <- s.apply("bob", "db", None).assertFailsWith[UnsupportedAuthenticationSchemeException]
      } yield ("ok")

    }
  }

}
