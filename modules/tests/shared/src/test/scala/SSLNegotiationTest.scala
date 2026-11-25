// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import com.comcast.ip4s._
import fs2.io.net.Network
import fs2.io.net.tls.TLSParameters
import skunk.net.SSLNegotiation

class SSLNegotiationTest extends ffstest.FTest {

  // Built like `Session.Builder.withHost` does: `Host.fromString` tries `IpAddress` first, unlike the `host` literal.
  private def options(params: TLSParameters): Resource[IO, SSLNegotiation.Options[IO]] =
    Network[IO].tlsContext.insecureResource.map(SSLNegotiation.Options(_, params, false, None))

  test("default parameters get the hostname as server name") {
    options(TLSParameters.Default).use { o =>
      val named = SSLNegotiation.withServerName(o, Host.fromString("db.example.com").get)
      assert("parameters", named.tlsParameters == SSLNegotiation.serverNameParameters("db.example.com"))
    }
  }

  test("an IP address host leaves the default parameters alone") {
    options(TLSParameters.Default).use { o =>
      val named = SSLNegotiation.withServerName(o, Host.fromString("127.0.0.1").get)
      assert("parameters", named.tlsParameters == TLSParameters.Default)
    }
  }

  test("explicitly configured parameters are left alone") {
    val custom = SSLNegotiation.serverNameParameters("other.example.com")
    options(custom).use { o =>
      val named = SSLNegotiation.withServerName(o, Host.fromString("db.example.com").get)
      assert("parameters", named.tlsParameters == custom)
    }
  }

}
